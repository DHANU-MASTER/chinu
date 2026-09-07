package com.aiteacher.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.aiteacher.ai.AiException;
import com.aiteacher.chat.ConversationHistoryService.ChatTurn;
import com.aiteacher.dto.AskTeacherRequest;
import com.aiteacher.service.AskTeacherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bidirectional ask-the-teacher chat over a raw WebSocket at {@code /ws/ask}.
 *
 * <p>Client → server JSON frames:
 * <ul>
 *   <li>{@code {"type":"ask", question, topic, sectionTitle, sectionContent, persona, language}}</li>
 *   <li>{@code {"type":"clear"}}</li>
 * </ul>
 *
 * <p>Server → client JSON frames:
 * <ul>
 *   <li>{@code {"type":"history", messages:[{role, text, at}]}} — replayed on connect</li>
 *   <li>{@code {"type":"delta", text}} — answer token while it is written</li>
 *   <li>{@code {"type":"answer", question, answer}} — the completed answer</li>
 *   <li>{@code {"type":"cleared"}}</li>
 *   <li>{@code {"type":"error", error}}</li>
 * </ul>
 *
 * <p>The chat id arrives as the {@code chatId} query parameter and keys the
 * conversation history, so reconnects replay the same conversation.
 */
@Component
public class AskTeacherWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AskTeacherWebSocketHandler.class);

    private final AskTeacherService askTeacherService;
    private final ConversationHistoryService historyService;
    private final ObjectMapper json;
    private final Executor executor;

    @Autowired
    public AskTeacherWebSocketHandler(AskTeacherService askTeacherService,
            ConversationHistoryService historyService, ObjectMapper json) {
        this(askTeacherService, historyService, json,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "ask-teacher-chat");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /** Test-visible constructor: pass a synchronous executor to run inline. */
    AskTeacherWebSocketHandler(AskTeacherService askTeacherService,
            ConversationHistoryService historyService, ObjectMapper json, Executor executor) {
        this.askTeacherService = askTeacherService;
        this.historyService = historyService;
        this.json = json;
        this.executor = executor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String chatId = chatId(session);
        try {
            send(session, Map.of(
                    "type", "history",
                    "messages", historyService.get(chatId)));
        } catch (Exception ex) {
            log.warn("Failed to replay history for chat {}: {}", chatId, ex.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode frame;
        try {
            frame = json.readTree(message.getPayload());
        } catch (Exception ex) {
            sendSafe(session, Map.of("type", "error", "error", "Unreadable message."));
            return;
        }

        String type = frame.path("type").asText("ask");
        String chatId = chatId(session);

        if ("clear".equals(type)) {
            historyService.clear(chatId);
            sendSafe(session, Map.of("type", "cleared"));
            return;
        }

        if (!"ask".equals(type)) {
            sendSafe(session, Map.of("type", "error", "error", "Unknown message type: " + type));
            return;
        }

        String question = frame.path("question").asText("").trim();
        if (question.isEmpty() || question.length() > 500) {
            sendSafe(session, Map.of("type", "error", "error", "question must be 1-500 characters"));
            return;
        }

        // Capture request fields on the WS thread; the AI call runs on the executor.
        AskTeacherRequest request = AskTeacherRequest.builder()
                .question(question)
                .topic(frame.path("topic").asText("General"))
                .sectionTitle(frame.path("sectionTitle").asText(""))
                .sectionContent(frame.path("sectionContent").asText(""))
                .persona(frame.path("persona").asText("chopper"))
                .language(frame.path("language").asText("English"))
                .previousTurns(historyService.recentTurnsForPrompt(chatId))
                .build();

        executor.execute(() -> handleAsk(session, chatId, request));
    }

    private void handleAsk(WebSocketSession session, String chatId, AskTeacherRequest request) {
        historyService.append(chatId, ChatTurn.user(request.getQuestion()));
        StringBuilder accumulated = new StringBuilder();

        try {
            askTeacherService.generateAnswerStreaming(request, delta -> {
                accumulated.append(delta);
                sendSafe(session, Map.of("type", "delta", "text", delta));
            });

            String answer = accumulated.toString().trim();
            historyService.append(chatId, ChatTurn.assistant(answer));
            sendSafe(session, Map.of(
                    "type", "answer",
                    "question", request.getQuestion(),
                    "answer", answer));
        } catch (AiException ex) {
            log.warn("Ask-teacher chat failed ({}): {}", ex.getKind(), ex.getMessage());
            sendSafe(session, Map.of("type", "error", "error",
                    ex.isUnavailable()
                            ? "AI chat is not configured. Set AI_API_KEY and restart."
                            : "The teacher could not answer right now. Please try again."));
        } catch (Exception ex) {
            log.error("Unexpected error in ask-teacher chat", ex);
            sendSafe(session, Map.of("type", "error", "error",
                    "The teacher could not answer right now. Please try again."));
        }
    }

    private String chatId(WebSocketSession session) {
        String query = session.getUri() == null ? "" : session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                int eq = param.indexOf('=');
                if (eq > 0 && param.substring(0, eq).equals("chatId")) {
                    String value = param.substring(eq + 1).trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return "anonymous-" + session.getId();
    }

    private void sendSafe(WebSocketSession session, Object payload) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        } catch (IOException ex) {
            log.debug("Could not deliver WS frame (client likely gone): {}", ex.getMessage());
        }
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        }
    }
}
