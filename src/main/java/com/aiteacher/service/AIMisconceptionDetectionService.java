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
import com.aiteacher.dto.MisconceptionRequest;
import com.aiteacher.dto.MisconceptionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 7 AI misconception detection. Sends the student's answer along
 * with the correct concept and lesson context to the AI provider for
 * semantic analysis. Identifies whether the student UNDERSTOOD, has a
 * PARTIAL understanding, a specific MISCONCEPTION, or does NOT_UNDERSTOOD.
 * Uses the student's teaching style to recommend the best re-teaching approach.
 * No hardcoded misconceptions or keyword matching exists here.
 */
@Service
public class AIMisconceptionDetectionService implements MisconceptionDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AIMisconceptionDetectionService.class);

    private static final String OUTPUT_SCHEMA = """
            {
              "understanding": "UNDERSTOOD or PARTIAL or MISCONCEPTION or NOT_UNDERSTOOD",
              "misconception": "...",
              "severity": "LOW or MEDIUM or HIGH",
              "explanationNeeded": true,
              "recommendedApproach": "SIMPLER or EXAMPLE_BASED or ANALOGY or STEP_BY_STEP or VISUAL or PRACTICAL"
            }
            """;

    private final AiChatClient chatClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AIMisconceptionDetectionService(AiChatClient chatClient, ObjectMapper json,
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
    public MisconceptionResponse detectMisconception(MisconceptionRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable("AI misconception detection is not configured: the AI_API_KEY environment variable is not set.");
        }

        String outputLanguage = languageName(request.getLanguage());

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildSystemPrompt(outputLanguage)),
                ChatMessage.user(buildUserPrompt(request)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        return parseResponse(rawContent);
    }

    /** System prompt that instructs the AI to analyze student understanding. */
    private String buildSystemPrompt(String outputLanguage) {
        return """
                You are an expert teacher analyzing a student's understanding of a concept.

                Your task is to determine the student's understanding level and identify any misconceptions.

                RULES:
                1. Analyze the STUDENT'S ANSWER against the CORRECT CONCEPT and LESSON CONTENT.
                2. Do NOT use simple keyword matching. Understand the MEANING of the student's answer.
                3. Determine understanding level:
                   - UNDERSTOOD: Student clearly demonstrates understanding of the key concept.
                   - PARTIAL: Student shows some understanding but is incomplete or has minor gaps.
                   - MISCONCEPTION: Student has a specific incorrect mental model or confused concept.
                   - NOT_UNDERSTOOD: Student does not understand the concept at all.
                4. For MISCONCEPTION: describe the specific misunderstanding and its severity.
                5. For PARTIAL: describe what's missing or incomplete.
                6. For NOT_UNDERSTOOD: explain what the student seems confused about.
                7. If the evaluation status is CORRECT, the understanding should typically be UNDERSTOOD.
                8. severity must be one of: LOW, MEDIUM, HIGH.
                9. recommendedApproach must be one of: SIMPLER, EXAMPLE_BASED, ANALOGY, STEP_BY_STEP, VISUAL, PRACTICAL.
                10. Choose the recommendedApproach based on the student's teaching style and the type of issue:
                    - If teaching style is "Example-Based", prefer EXAMPLE_BASED or ANALOGY.
                    - If teaching style is "Step-by-Step", prefer STEP_BY_STEP or SIMPLER.
                    - If teaching style is "Visual", prefer VISUAL or EXAMPLE_BASED.
                    - If teaching style is "Simple Explanation", prefer SIMPLER or ANALOGY.
                11. explanationNeeded should be true for PARTIAL, MISCONCEPTION, NOT_UNDERSTOOD.
                12. explanationNeeded should be false for UNDERSTOOD.
                13. Write EVERYTHING in %s.

                OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
                Use exactly this schema:
                %s
                """.formatted(outputLanguage, OUTPUT_SCHEMA);
    }

    /** User prompt carrying the actual question, answer, and lesson context. */
    private String buildUserPrompt(MisconceptionRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Student Education Level: ").append(request.getEducationLevel()).append('\n');
        prompt.append("Student Teaching Style: ").append(request.getTeachingStyle()).append('\n');
        prompt.append("Learning Objective: ").append(request.getObjective()).append('\n');
        prompt.append("Topic: ").append(request.getTopic()).append('\n');
        prompt.append("\nLesson Section: ").append(request.getSectionTitle()).append('\n');
        prompt.append("Section Content:\n").append(request.getLessonSection()).append('\n');
        prompt.append("\nQuestion: ").append(request.getQuestion()).append('\n');
        prompt.append("Student's Answer: ").append(request.getStudentAnswer()).append('\n');
        prompt.append("Correct Concept: ").append(request.getCorrectConcept()).append('\n');
        prompt.append("Evaluation Status: ").append(request.getEvaluationStatus()).append('\n');
        prompt.append("Evaluation Feedback: ").append(request.getEvaluationFeedback()).append('\n');
        prompt.append("\nAnalyze the student's understanding. Is there a misconception? What approach would help them understand better?");
        return prompt.toString();
    }

    /** Parses the AI's JSON response into a MisconceptionResponse. */
    private MisconceptionResponse parseResponse(String rawContent) {
        JsonNode root;
        try {
            root = json.readTree(stripJsonFences(rawContent));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw AiException.invalidResponse("AI returned content that is not a JSON object");
        }

        String understanding = normalizeUnderstanding(requiredText(root, "understanding", "understanding"));
        String misconception = clean(root.path("misconception").asText(null));
        String severity = normalizeSeverity(clean(root.path("severity").asText(null)));
        boolean explanationNeeded = root.path("explanationNeeded").asBoolean(!"UNDERSTOOD".equals(understanding));
        String recommendedApproach = normalizeApproach(requiredText(root, "recommendedApproach", "recommendedApproach"));

        return MisconceptionResponse.builder()
                .understanding(understanding)
                .misconception(misconception)
                .severity(severity)
                .explanationNeeded(explanationNeeded)
                .recommendedApproach(recommendedApproach)
                .build();
    }

    /** Normalizes understanding to allowed values. */
    private String normalizeUnderstanding(String value) {
        String upper = value.toUpperCase(Locale.ROOT).trim();
        if (upper.contains("UNDERSTOOD") && !upper.contains("NOT")) {
            return "UNDERSTOOD";
        }
        if (upper.contains("PARTIAL")) {
            return "PARTIAL";
        }
        if (upper.contains("MISCONCEPTION")) {
            return "MISCONCEPTION";
        }
        return "NOT_UNDERSTOOD";
    }

    /** Normalizes severity to allowed values. */
    private String normalizeSeverity(String value) {
        if (value == null) return "MEDIUM";
        String upper = value.toUpperCase(Locale.ROOT).trim();
        if (upper.contains("LOW")) return "LOW";
        if (upper.contains("HIGH")) return "HIGH";
        return "MEDIUM";
    }

    /** Normalizes recommended approach to allowed values. */
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
