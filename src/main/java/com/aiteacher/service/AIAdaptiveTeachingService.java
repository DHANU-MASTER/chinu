package com.aiteacher.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;
import com.aiteacher.dto.AdaptiveTeachingRequest;
import com.aiteacher.dto.AdaptiveTeachingResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 7 AI adaptive re-teaching. When a student struggles with a concept,
 * this service asks the AI to generate a DIFFERENT explanation using a new
 * teaching approach (analogy, step-by-step, example-based, etc.). Also
 * generates a follow-up question to verify the student now understands.
 * The adaptation is always based on the actual lesson content and student's
 * specific misconception — never from hardcoded responses.
 */
@Service
public class AIAdaptiveTeachingService implements AdaptiveTeachingService {

    private static final Logger log = LoggerFactory.getLogger(AIAdaptiveTeachingService.class);

    private static final String OUTPUT_SCHEMA = """
            {
              "adaptationType": "SIMPLER or EXAMPLE_BASED or ANALOGY or STEP_BY_STEP or VISUAL or PRACTICAL",
              "misconception": "...",
              "reExplanation": "...",
              "example": "...",
              "followUpQuestion": "...",
              "followUpQuestionType": "MCQ or SHORT_ANSWER",
              "followUpOptions": ["...", "...", "...", "..."],
              "followUpCorrectOptionIndex": 0
            }
            """;

    private final AiChatClient chatClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AIAdaptiveTeachingService(AiChatClient chatClient, ObjectMapper json,
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
    public AdaptiveTeachingResponse generateAdaptation(AdaptiveTeachingRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable("AI adaptive teaching is not configured: the AI_API_KEY environment variable is not set.");
        }

        String outputLanguage = languageName(request.getLanguage());

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildSystemPrompt(outputLanguage)),
                ChatMessage.user(buildUserPrompt(request)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        return parseResponse(rawContent);
    }

    /** System prompt that instructs the AI to generate adaptive re-teaching. */
    private String buildSystemPrompt(String outputLanguage) {
        return """
                You are an expert adaptive AI teacher. A student is struggling with a concept.
                Your job is to explain it DIFFERENTLY using a new teaching approach.

                CRITICAL RULES:
                1. The reExplanation MUST be DIFFERENT from the original explanation.
                2. Do NOT simply repeat the same explanation. Use a NEW approach.
                3. Choose the adaptationType based on the recommended approach and the student's profile.
                4. The example must be a NEW, concrete illustration — not the same one from the lesson.
                5. The followUpQuestion must test the SAME concept but be a DIFFERENT question.
                6. For MCQ followUp: provide exactly 4 options with correctOptionIndex.
                7. For SHORT_ANSWER followUp: set followUpOptions to [] and followUpCorrectOptionIndex to null.
                8. Write EVERYTHING in %s.

                Available adaptation types:
                - SIMPLER: Use simpler language, avoid jargon, break it down.
                - EXAMPLE_BASED: Use a concrete, relatable real-world example.
                - ANALOGY: Compare the concept to something familiar.
                - STEP_BY_STEP: Break the explanation into numbered steps.
                - VISUAL: Describe a visual representation or mental image.
                - PRACTICAL: Show how the concept is applied in practice.

                OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
                Use exactly this schema:
                %s
                """.formatted(outputLanguage, OUTPUT_SCHEMA);
    }

    /** User prompt carrying the misconception analysis and lesson context. */
    private String buildUserPrompt(AdaptiveTeachingRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Student Education Level: ").append(request.getEducationLevel()).append('\n');
        prompt.append("Student Teaching Style: ").append(request.getTeachingStyle()).append('\n');
        prompt.append("Learning Objective: ").append(request.getObjective()).append('\n');
        prompt.append("Topic: ").append(request.getTopic()).append('\n');
        prompt.append("\nLesson Section: ").append(request.getSectionTitle()).append('\n');
        prompt.append("Original Explanation:\n").append(request.getOriginalExplanation()).append('\n');
        if (request.getOriginalExample() != null && !request.getOriginalExample().isBlank()) {
            prompt.append("Original Example:\n").append(request.getOriginalExample()).append('\n');
        }
        prompt.append("\nQuestion Asked: ").append(request.getQuestion()).append('\n');
        prompt.append("Student's Answer: ").append(request.getStudentAnswer()).append('\n');
        prompt.append("\nMisconception Analysis:\n");
        prompt.append("Understanding Level: ").append(request.getUnderstanding()).append('\n');
        prompt.append("Identified Misconception: ").append(request.getMisconception()).append('\n');
        prompt.append("Recommended Approach: ").append(request.getRecommendedApproach()).append('\n');
        prompt.append("Adaptation Attempt: ").append(request.getAdaptationCount() + 1).append('\n');
        prompt.append("\nGenerate a DIFFERENT explanation using the recommended approach. ");
        prompt.append("Create a new follow-up question to check if the student now understands.");
        return prompt.toString();
    }

    /** Parses the AI's JSON response into an AdaptiveTeachingResponse. */
    private AdaptiveTeachingResponse parseResponse(String rawContent) {
        JsonNode root;
        try {
            root = json.readTree(stripJsonFences(rawContent));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw AiException.invalidResponse("AI returned content that is not a JSON object");
        }

        String adaptationType = normalizeApproach(requiredText(root, "adaptationType", "adaptationType"));
        String misconception = clean(root.path("misconception").asText(null));
        String reExplanation = requiredText(root, "reExplanation", "reExplanation");
        String example = clean(root.path("example").asText(null));
        String followUpQuestion = requiredText(root, "followUpQuestion", "followUpQuestion");
        String followUpQuestionType = normalizeQuestionType(clean(root.path("followUpQuestionType").asText(null)));

        List<String> followUpOptions = new ArrayList<>();
        Integer followUpCorrectOptionIndex = null;

        if ("MCQ".equalsIgnoreCase(followUpQuestionType)) {
            JsonNode optionsNode = root.path("followUpOptions");
            if (optionsNode.isArray()) {
                for (JsonNode node : optionsNode) {
                    String option = clean(node.asText(null));
                    if (option != null) {
                        followUpOptions.add(option);
                    }
                }
            }
            if (followUpOptions.size() != 4) {
                // If MCQ but wrong option count, fall back to SHORT_ANSWER
                followUpQuestionType = "SHORT_ANSWER";
                followUpOptions.clear();
            } else {
                JsonNode indexNode = root.path("followUpCorrectOptionIndex");
                if (indexNode.isIntegralNumber()) {
                    followUpCorrectOptionIndex = indexNode.asInt();
                    if (followUpCorrectOptionIndex < 0 || followUpCorrectOptionIndex > 3) {
                        followUpCorrectOptionIndex = 0;
                    }
                } else {
                    followUpCorrectOptionIndex = 0;
                }
            }
        }

        return AdaptiveTeachingResponse.builder()
                .adaptationType(adaptationType)
                .misconception(misconception)
                .reExplanation(reExplanation)
                .example(example)
                .followUpQuestion(followUpQuestion)
                .followUpQuestionType(followUpQuestionType)
                .followUpOptions(followUpOptions)
                .followUpCorrectOptionIndex(followUpCorrectOptionIndex)
                .build();
    }

    /** Normalizes question type. */
    private String normalizeQuestionType(String value) {
        if (value == null) return "SHORT_ANSWER";
        String upper = value.toUpperCase(Locale.ROOT).trim();
        if (upper.contains("MCQ")) return "MCQ";
        return "SHORT_ANSWER";
    }

    /** Normalizes approach to allowed values. */
    private String normalizeApproach(String value) {
        String upper = value.toUpperCase(Locale.ROOT).trim().replace("-", "_").replace(" ", "_");
        if (upper.contains("SIMPLER")) return "SIMPLER";
        if (upper.contains("EXAMPLE")) return "EXAMPLE_BASED";
        if (upper.contains("ANALOGY")) return "ANALOGY";
        if (upper.contains("STEP")) return "STEP_BY_STEP";
        if (upper.contains("VISUAL")) return "VISUAL";
        if (upper.contains("PRACTICAL")) return "PRACTICAL";
        return "EXAMPLE_BASED";
    }

    /** Removes markdown fences around JSON. */
    private String stripJsonFences(String raw) {
        if (raw == null) {
            throw AiException.invalidResponse("AI returned no content");
        }
        String trimmed = raw.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    /** Maps language codes/names to full language names for the prompt. */
    private String languageName(String language) {
        if (language == null) {
            return "English";
        }
        String value = language.toLowerCase(Locale.ROOT);
        if (value.contains("hindi") || value.startsWith("hi")) {
            return "Hindi";
        }
        if (value.contains("kannada") || value.startsWith("kn")) {
            return "Kannada";
        }
        return "English";
    }

    private String requiredText(JsonNode root, String field, String label) {
        String value = clean(root.path(field).asText(null));
        if (value == null) {
            throw AiException.invalidResponse("AI response is missing required field: " + label);
        }
        return value;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
