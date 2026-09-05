package com.aiteacher.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;
import com.aiteacher.dto.QuestionRequest;
import com.aiteacher.dto.QuestionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 6 AI question generation. Sends the current lesson section content
 * to the AI provider and asks it to generate a single question — either
 * MCQ or SHORT_ANSWER — based on what was just taught. The question type
 * is randomly chosen to provide variety. Every question is generated
 * dynamically from real lesson content; no hardcoded question bank exists.
 */
@Service
public class AIQuestionGenerationService implements QuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AIQuestionGenerationService.class);

    private static final String OUTPUT_SCHEMA = """
            {
              "type": "MCQ or SHORT_ANSWER",
              "question": "...",
              "options": ["...", "...", "...", "..."],
              "correctOptionIndex": 0,
              "expectedConcept": "..."
            }
            """;

    private final AiChatClient chatClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AIQuestionGenerationService(AiChatClient chatClient, ObjectMapper json,
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
    public QuestionResponse generateQuestion(QuestionRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable("AI question generation is not configured: the AI_API_KEY environment variable is not set.");
        }

        String outputLanguage = languageName(request.getLanguage());
        String questionType = decideQuestionType();

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildSystemPrompt(outputLanguage, questionType)),
                ChatMessage.user(buildUserPrompt(request)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        return parseQuestion(rawContent);
    }

    /** Randomly choose between MCQ and SHORT_ANSWER to provide variety. */
    private String decideQuestionType() {
        return Math.random() < 0.6 ? "MCQ" : "SHORT_ANSWER";
    }

    /** System prompt that instructs the AI to generate a question from lesson content. */
    private String buildSystemPrompt(String outputLanguage, String questionType) {
        String typeInstruction;
        if ("MCQ".equals(questionType)) {
            typeInstruction = """
                    Generate a multiple-choice question (MCQ) with exactly 4 options.
                    The "type" field must be "MCQ".
                    "options" must contain exactly 4 strings.
                    "correctOptionIndex" must be the zero-based index of the correct option (0-3).
                    """;
        } else {
            typeInstruction = """
                    Generate a short-answer question.
                    The "type" field must be "SHORT_ANSWER".
                    "options" should be an empty array [].
                    "correctOptionIndex" should be null.
                    "expectedConcept" must describe the key concept the answer should demonstrate.
                    """;
        }		return """
				You are an expert teacher creating a check-your-understanding question.

				RULES:
				1. The question MUST be based on the lesson content provided by the user.
				2. Do NOT use any knowledge outside the provided lesson content.
				3. The question must test understanding, not just recall.
				4. Adapt difficulty to the student's education level AND prior knowledge
				   when prior knowledge is provided (do not ask what they already know).
				5. Write EVERYTHING (question, options, expectedConcept) in %s.
				6. For MCQ, make the incorrect options plausible but clearly wrong.
				7. For SHORT_ANSWER, the expectedConcept should describe what a good answer demonstrates.
				8. Do NOT use hardcoded or generic questions. Base everything on the supplied content.

				%s

				OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
				Use exactly this schema:
				%s
				""".formatted(outputLanguage, typeInstruction, OUTPUT_SCHEMA);
    }

    /** User prompt carrying the actual lesson section content. */
    private String buildUserPrompt(QuestionRequest request) {
        StringBuilder prompt = new StringBuilder();		prompt.append("Student Education Level: ").append(request.getEducationLevel()).append('\n');
		prompt.append("Language: ").append(request.getLanguage()).append('\n');
		prompt.append("Topic: ").append(request.getTopic()).append('\n');
		prompt.append("Learning Objective: ").append(request.getObjective()).append('\n');
		if (request.getPriorKnowledge() != null && !request.getPriorKnowledge().isBlank()) {
			prompt.append("Existing Knowledge: ").append(request.getPriorKnowledge()).append('\n');
		}
		if (request.getDesiredDepth() != null && !request.getDesiredDepth().isBlank()) {
			prompt.append("Desired Depth: ").append(request.getDesiredDepth()).append('\n');
		}
        prompt.append("\nCurrent Lesson Section:\n");
        prompt.append("Title: ").append(request.getSectionTitle()).append('\n');
        prompt.append("Content:\n").append(request.getSectionContent()).append('\n');
        if (request.getSectionExample() != null && !request.getSectionExample().isBlank()) {
            prompt.append("Example:\n").append(request.getSectionExample()).append('\n');
        }
        prompt.append("\nGenerate ONE question based on this section content.");
        return prompt.toString();
    }

    /** Parses the AI's JSON response into a QuestionResponse. */
    private QuestionResponse parseQuestion(String rawContent) {
        JsonNode root;
        try {
            root = json.readTree(stripJsonFences(rawContent));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw AiException.invalidResponse("AI returned content that is not a JSON object");
        }

        String type = requiredText(root, "type", "type");
        String question = requiredText(root, "question", "question");
        String expectedConcept = clean(root.path("expectedConcept").asText(null));

        List<String> options = new ArrayList<>();
        Integer correctOptionIndex = null;

        if ("MCQ".equalsIgnoreCase(type)) {
            JsonNode optionsNode = root.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode node : optionsNode) {
                    String option = clean(node.asText(null));
                    if (option != null) {
                        options.add(option);
                    }
                }
            }
            if (options.size() != 4) {
                throw AiException.invalidResponse("MCQ must have exactly 4 options, got " + options.size());
            }
            JsonNode indexNode = root.path("correctOptionIndex");
            if (indexNode.isIntegralNumber()) {
                correctOptionIndex = indexNode.asInt();
                if (correctOptionIndex < 0 || correctOptionIndex > 3) {
                    throw AiException.invalidResponse("correctOptionIndex must be 0-3");
                }
            } else {
                throw AiException.invalidResponse("MCQ missing correctOptionIndex");
            }
        }

        return QuestionResponse.builder()
                .type(type.toUpperCase(Locale.ROOT))
                .question(question)
                .options(options)
                .correctOptionIndex(correctOptionIndex)
                .expectedConcept(expectedConcept)
                .questionId(UUID.randomUUID().toString())
                .build();
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
