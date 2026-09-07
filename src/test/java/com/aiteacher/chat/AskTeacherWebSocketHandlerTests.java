package com.aiteacher.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.aiteacher.dto.AskTeacherRequest;
import com.aiteacher.dto.AskTeacherResponse;
import com.aiteacher.service.AskTeacherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for the ask-the-teacher WebSocket handler using a synchronous
 * executor, a scripted fake AI service, and a capturing WebSocket session.
 */
class AskTeacherWebSocketHandlerTests {

    private final ObjectMapper json = new ObjectMapper();
    private final LinkedBlockingQueue<String> sentFrames = new LinkedBlockingQueue<>();
    private AskTeacherService askService;
    private ConversationHistoryService history;
    private AskTeacherWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        askService = mock(AskTeacherService.class);
        history = new ConversationHistoryService();
        // Synchronous executor: handleAsk runs inline, so tests are deterministic.
        handler = new AskTeacherWebSocketHandler(askService, history, json, Runnable::run);

        session = mock(WebSocketSession.class);
        try {
            when(session.getUri()).thenReturn(new URI("ws://localhost/ws/ask?chatId=chat-1"));
            doAnswer(invocation -> {
                sentFrames.add(((TextMessage) invocation.getArgument(0)).getPayload());
                return null;
            }).when(session).sendMessage(any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String nextFrame() throws Exception {
        String frame = sentFrames.poll(2, TimeUnit.SECONDS);
        if (frame == null) {
            throw new AssertionError("No WebSocket frame arrived in time");
        }
        return frame;
    }

    private JsonNode ask(String payload) throws Exception {
        handler.handleTextMessage(session, new TextMessage(payload));
        return json.readTree(nextFrame());
    }

    @Test
    void connectReplaysConversationHistory() throws Exception {
        history.append("chat-1", ConversationHistoryService.ChatTurn.user("Earlier question"));
        history.append("chat-1", ConversationHistoryService.ChatTurn.assistant("Earlier answer"));

        handler.afterConnectionEstablished(session);

        JsonNode frame = json.readTree(nextFrame());
        assertEquals("history", frame.path("type").asText());
        assertEquals(2, frame.path("messages").size());
        assertEquals("Earlier question", frame.path("messages").get(0).path("text").asText());
    }

    @Test
    void askStreamsDeltasThenFinalAnswerAndStoresHistory() throws Exception {
        doAnswer(invocation -> {
            java.util.function.Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("Gravity ");
            onDelta.accept("pulls objects together.");
            return AskTeacherResponse.builder().answer("Gravity pulls objects together.").build();
        }).when(askService).generateAnswerStreaming(any(AskTeacherRequest.class), any());

        JsonNode first = ask("{\"type\":\"ask\",\"question\":\"What is gravity?\",\"topic\":\"Physics\"}");
        assertEquals("delta", first.path("type").asText());
        assertEquals("Gravity ", first.path("text").asText());

        JsonNode second = json.readTree(nextFrame());
        assertEquals("delta", second.path("type").asText());

        JsonNode answer = json.readTree(nextFrame());
        assertEquals("answer", answer.path("type").asText());
        assertEquals("What is gravity?", answer.path("question").asText());
        assertEquals("Gravity pulls objects together.", answer.path("answer").asText());

        // History now holds the turn pair for the AI prompt and reconnect replay.
        List<String> promptTurns = history.recentTurnsForPrompt("chat-1");
        assertEquals(2, promptTurns.size());
        assertTrue(promptTurns.get(0).startsWith("Student: What is gravity?"));
    }

    @Test
    void askIncludesPreviousTurnsForContext() throws Exception {
        history.append("chat-1", ConversationHistoryService.ChatTurn.user("Earlier question"));

        AtomicReference<List<String>> capturedTurns = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            capturedTurns.set(invocation.getArgument(0, AskTeacherRequest.class).getPreviousTurns());
            return AskTeacherResponse.builder().answer("ok").build();
        }).when(askService).generateAnswerStreaming(any(AskTeacherRequest.class), any());

        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"ask\",\"question\":\"Follow-up?\"}"));
        nextFrame(); // answer

        assertEquals(1, capturedTurns.get().size());
        assertTrue(capturedTurns.get().get(0).contains("Earlier question"));
    }

    @Test
    void blankQuestionReturnsErrorFrame() throws Exception {
        JsonNode frame = ask("{\"type\":\"ask\",\"question\":\"   \"}");
        assertEquals("error", frame.path("type").asText());
        assertTrue(frame.path("error").asText().contains("1-500"));
    }

    @Test
    void unknownTypeReturnsErrorFrame() throws Exception {
        JsonNode frame = ask("{\"type\":\"dance\"}");
        assertEquals("error", frame.path("type").asText());
    }

    @Test
    void unreadableJsonReturnsErrorFrame() throws Exception {
        handler.handleTextMessage(session, new TextMessage("not json at all"));
        JsonNode frame = json.readTree(nextFrame());
        assertEquals("error", frame.path("type").asText());
    }

    @Test
    void aiFailureMapsToSafeErrorFrame() throws Exception {
        doAnswer(invocation -> {
            throw com.aiteacher.ai.AiException.unavailable("AI_API_KEY missing");
        }).when(askService).generateAnswerStreaming(any(AskTeacherRequest.class), any());

        JsonNode frame = ask("{\"type\":\"ask\",\"question\":\"Hi\"}");
        assertEquals("error", frame.path("type").asText());
        assertTrue(frame.path("error").asText().contains("AI_API_KEY"));
    }

    @Test
    void clearDropsHistoryAndConfirms() throws Exception {
        history.append("chat-1", ConversationHistoryService.ChatTurn.user("old"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"clear\"}"));

        JsonNode frame = json.readTree(nextFrame());
        assertEquals("cleared", frame.path("type").asText());
        assertTrue(history.get("chat-1").isEmpty());
    }

}
