package com.aiteacher.service;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;
import com.aiteacher.dto.AskTeacherRequest;
import com.aiteacher.dto.AskTeacherResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI-backed ask-the-teacher: the student asks any follow-up question during a
 * lesson and the current teacher persona answers in one or two sentences,
 * grounded strictly in the section content that is being taught.
 */
@Service
public class AIAskTeacherService implements AskTeacherService {

    private static final Logger log = LoggerFactory.getLogger(AIAskTeacherService.class);

    private static final String OUTPUT_SCHEMA = """
            {
              "answer": "one or two sentence answer in the teacher's voice"
            }
            """;

    private final AiChatClient chatClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AIAskTeacherService(AiChatClient chatClient, ObjectMapper json,
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_BASE_URL:https://api.openai.com/v1}") String baseUrl,
            @Value("${AI_MODEL:gpt-4o-mini}") String model) {
        this.chatClient = chatClient;
        this.json = json;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public AskTeacherResponse answerQuestion(AskTeacherRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable("AI ask-the-teacher is not configured: the AI_API_KEY environment variable is not set.");
        }

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, prepareMessages(request));
        return parseAnswer(rawContent);
    }

    @Override
    public AskTeacherResponse generateAnswerStreaming(AskTeacherRequest request,
            java.util.function.Consumer<String> onDelta) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable("AI ask-the-teacher is not configured: the AI_API_KEY environment variable is not set.");
        }

        StringBuilder accumulated = new StringBuilder();
        chatClient.streamChatCompletion(baseUrl, apiKey.trim(), model, prepareMessages(request), delta -> {
            accumulated.append(delta);
            onDelta.accept(delta);
        });

        return parseAnswer(accumulated.toString());
    }

    /** Builds the exact chat messages used for ask-the-teacher. */
    public List<ChatMessage> prepareMessages(AskTeacherRequest request) {
        String outputLanguage = languageName(request.getLanguage());
        return List.of(
                ChatMessage.system(buildSystemPrompt(outputLanguage, request.getPersona())),
                ChatMessage.user(buildUserPrompt(request)));
    }

    private String buildSystemPrompt(String outputLanguage, String persona) {
        return """
                You are an AI teacher persona named "%s" teaching a live lesson.
                A student just asked a follow-up question in the middle of the current section.
                Answer in at most two short sentences, in a warm, encouraging teacher voice,
                staying strictly grounded in the section content — do not introduce unrelated topics.
                Reply ONLY with compact JSON matching this schema (no markdown, no code fences):
                %s
                Write the "answer" value in %s.
                """.formatted(personaLabel(persona), OUTPUT_SCHEMA.stripIndent(), outputLanguage);
    }

    private String buildUserPrompt(AskTeacherRequest request) {
        StringBuilder conversation = new StringBuilder();
        List<String> previousTurns = request.getPreviousTurns();
        if (previousTurns != null && !previousTurns.isEmpty()) {
            conversation.append("Recent conversation (for continuity):\n");
            previousTurns.forEach(turn -> conversation.append("- ").append(turn).append('\n'));
            conversation.append('\n');
        }

        return """
                %sLesson topic: %s
                Current section: %s
                Section content: %s

                Student's question: %s
                """.formatted(
                conversation,
                orDefault(request.getTopic(), "General"),
                orDefault(request.getSectionTitle(), "Current section"),
                truncate(orDefault(request.getSectionContent(), ""), 2500),
                orDefault(request.getQuestion(), ""));
    }

    private AskTeacherResponse parseAnswer(String rawContent) {
        try {
            JsonNode root = json.readTree(rawContent);
            String answer = root.path("answer").asText("");
            if (answer.isBlank()) {
                answer = root.path("feedback").asText("");
            }
            if (answer.isBlank()) {
                throw new IllegalStateException("Empty answer field");
            }
            return AskTeacherResponse.builder().answer(answer.trim()).build();
        } catch (Exception ex) {
            log.warn("Failed to parse ask-teacher response: {}", rawContent);
            throw AiException.invalidResponse("The AI reply could not be parsed. Please ask again.");
        }
    }

    private static String personaLabel(String persona) {
        return switch (persona == null ? "" : persona.toLowerCase(Locale.ROOT)) {
            case "shanks" -> "Red-Haired Shanks — the inspiring, bold captain";
            case "lucky_roux" -> "Lucky Roux — the quick, friendly, fun specialist";
            default -> "Chopper — the kind, clever doctor";
        };
    }

    private static String languageName(String code) {
        return switch (code == null ? "" : code.toLowerCase(Locale.ROOT)) {
            case "hi", "hindi" -> "Hindi";
            case "kn", "kannada" -> "Kannada";
            default -> "English";
        };
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
