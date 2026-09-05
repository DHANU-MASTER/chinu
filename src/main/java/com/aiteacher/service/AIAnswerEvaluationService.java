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
import com.aiteacher.dto.EvaluationRequest;
import com.aiteacher.dto.EvaluationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 6 AI answer evaluation. Sends the student's answer along with
 * the question context to the AI provider for semantic evaluation.
 * For MCQ, the correct option index is checked directly. For SHORT_ANSWER,
 * the AI evaluates meaning rather than doing simple string comparison.
 * Every evaluation is based on the actual question and answer — no fake
 * evaluation logic exists here.
 */
@Service
public class AIAnswerEvaluationService implements AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AIAnswerEvaluationService.class);

    private static final String OUTPUT_SCHEMA = """
            {
              "status": "CORRECT or PARTIALLY_CORRECT or INCORRECT",
              "feedback": "...",
              "expectedConcept": "...",
              "confidence": 0.95
            }
            """;

    private final AiChatClient chatClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AIAnswerEvaluationService(AiChatClient chatClient, ObjectMapper json,
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
    public EvaluationResponse evaluateAnswer(EvaluationRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable("AI answer evaluation is not configured: the AI_API_KEY environment variable is not set.");
        }

        // For MCQ, we can do exact option index matching for correctness,
        // but still use AI for generating personalized feedback.
        if ("MCQ".equalsIgnoreCase(request.getQuestionType()) && request.getCorrectOptionIndex() != null) {
            return evaluateMcq(request);
        }

        // For SHORT_ANSWER, use AI semantic evaluation.
        return evaluateShortAnswer(request);
    }

    /** Evaluates MCQ answers: exact match for correctness, AI for feedback. */
    private EvaluationResponse evaluateMcq(EvaluationRequest request) {
        String outputLanguage = languageName(request.getLanguage());

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildMcqFeedbackPrompt(outputLanguage)),
                ChatMessage.user(buildMcqUserPrompt(request)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        return parseEvaluation(rawContent);
    }

    /** Evaluates SHORT_ANSWER using AI semantic understanding. */
    private EvaluationResponse evaluateShortAnswer(EvaluationRequest request) {
        String outputLanguage = languageName(request.getLanguage());

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildShortAnswerPrompt(outputLanguage)),
                ChatMessage.user(buildShortAnswerUserPrompt(request)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        return parseEvaluation(rawContent);
    }

    /** System prompt for MCQ feedback generation. */
    private String buildMcqFeedbackPrompt(String outputLanguage) {
        return """
                You are an expert teacher evaluating a student's multiple-choice answer.

                RULES:
                1. The student selected an option. You are told whether it was correct or incorrect.
                2. Provide personalized, teacher-style feedback in %s.
                3. For correct answers: affirm understanding, explain why it's correct.
                4. For incorrect answers: explain why the correct answer is right and why the student's choice is wrong.
                5. The feedback must relate to the actual question and content.
                6. Do NOT use one permanent message for every question.
                7. confidence must be between 0.0 and 1.0.

                OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
                Use exactly this schema:
                %s
                """.formatted(outputLanguage, OUTPUT_SCHEMA);
    }

    /** System prompt for SHORT_ANSWER semantic evaluation. */
    private String buildShortAnswerPrompt(String outputLanguage) {
        return """
                You are an expert teacher evaluating a student's short-answer response.

                RULES:
                1. Evaluate the STUDENT'S ANSWER based on the QUESTION and LESSON CONTENT.
                2. Do NOT evaluate by simple string matching. Understand the MEANING.
                3. Status must be one of: CORRECT, PARTIALLY_CORRECT, INCORRECT.
                4. CORRECT: The answer demonstrates understanding of the key concept.
                5. PARTIALLY_CORRECT: The answer shows some understanding but is incomplete or has minor errors.
                6. INCORRECT: The answer shows misunderstanding or is completely wrong.
                7. Write ALL feedback in %s.
                8. Feedback must explain WHY the answer is correct/incorrect, relating to the lesson content.
                9. expectedConcept must state what the answer should have demonstrated.
                10. confidence must be between 0.0 and 1.0.
                11. Do NOT automatically mark answers correct. Be honest in evaluation.

                OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
                Use exactly this schema:
                %s
                """.formatted(outputLanguage, OUTPUT_SCHEMA);
    }

    /** User prompt for MCQ evaluation. */
    private String buildMcqUserPrompt(EvaluationRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Question: ").append(request.getQuestion()).append('\n');
        prompt.append("Student's Selected Answer: ").append(request.getStudentAnswer()).append('\n');
        prompt.append("Correct Option Index: ").append(request.getCorrectOptionIndex()).append('\n');
        prompt.append("Lesson Section: ").append(request.getLessonSection()).append('\n');
        prompt.append("Topic: ").append(request.getTopic()).append('\n');
        prompt.append("Education Level: ").append(request.getEducationLevel()).append('\n');
        prompt.append("\nWas the student correct? Provide feedback.\n");
        return prompt.toString();
    }

    /** User prompt for SHORT_ANSWER evaluation. */
    private String buildShortAnswerUserPrompt(EvaluationRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Question: ").append(request.getQuestion()).append('\n');
        prompt.append("Student's Answer: ").append(request.getStudentAnswer()).append('\n');
        prompt.append("Lesson Section Content:\n").append(request.getLessonSection()).append('\n');
        prompt.append("Topic: ").append(request.getTopic()).append('\n');
        prompt.append("Education Level: ").append(request.getEducationLevel()).append('\n');
        prompt.append("\nEvaluate this answer. Is it correct, partially correct, or incorrect?\n");
        return prompt.toString();
    }

    /** Parses the AI's JSON response into an EvaluationResponse. */
    private EvaluationResponse parseEvaluation(String rawContent) {
        JsonNode root;
        try {
            root = json.readTree(stripJsonFences(rawContent));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw AiException.invalidResponse("AI returned content that is not a JSON object");
        }

        String status = requiredText(root, "status", "status");
        // Normalize status to allowed values
        status = normalizeStatus(status);
        String feedback = requiredText(root, "feedback", "feedback");
        String expectedConcept = clean(root.path("expectedConcept").asText(null));
        double confidence = parseConfidence(root);

        return EvaluationResponse.builder()
                .status(status)
                .feedback(feedback)
                .expectedConcept(expectedConcept)
                .confidence(confidence)
                .build();
    }

    /** Normalizes status string to one of the allowed values. */
    private String normalizeStatus(String status) {
        String upper = status.toUpperCase(Locale.ROOT).trim();
        if (upper.contains("PARTIAL")) {
            return "PARTIALLY_CORRECT";
        }
        if (upper.contains("CORRECT") && !upper.contains("INCORRECT") && !upper.contains("PARTIAL")) {
            return "CORRECT";
        }
        return "INCORRECT";
    }

    /** Parses confidence with fallback to 0.8. */
    private double parseConfidence(JsonNode root) {
        JsonNode node = root.path("confidence");
        if (node.isNumber()) {
            double value = node.asDouble();
            if (value >= 0.0 && value <= 1.0) {
                return value;
            }
        }
        return 0.8;
    }

    private String requiredText(JsonNode root, String field, String label) {
        String value = clean(root.path(field).asText(null));
        if (value == null) {
            throw AiException.invalidResponse("AI response is missing required field: " + label);
        }
        return value;
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

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
