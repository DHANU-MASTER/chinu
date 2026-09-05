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
import com.aiteacher.dto.AssessmentQuestion;
import com.aiteacher.dto.AssessmentRequest;
import com.aiteacher.dto.AssessmentResponse;
import com.aiteacher.dto.AssessmentResult;
import com.aiteacher.dto.AssessmentSubmissionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 8 AI assessment service. Generates dynamic questions from actual
 * lesson content and evaluates student answers using AI semantic understanding.
 * No hardcoded questions, no fake scores — everything comes from real data.
 */
@Service
public class AIAssessmentService implements AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AIAssessmentService.class);

    private static final String GENERATION_SCHEMA = """
            {
              "questions": [
                {
                  "type": "MCQ or SHORT_ANSWER or APPLICATION",
                  "question": "...",
                  "options": ["...", "...", "...", "..."],
                  "correctOptionIndex": 0,
                  "concept": "..."
                }
              ]
            }
            """;

    private static final String EVALUATION_SCHEMA = """
            {
              "score": 4,
              "total": 5,
              "percentage": 80.0,
              "conceptsUnderstood": ["..."],
              "weakAreas": ["..."],
              "incorrectConcepts": ["..."],
              "revisionRecommendations": [
                {
                  "concept": "...",
                  "recommendation": "..."
                }
              ],
              "suggestedNextTopic": "...",
              "performanceSummary": "..."
            }
            """;

    private final AiChatClient chatClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AIAssessmentService(AiChatClient chatClient, ObjectMapper json,
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
    public AssessmentResponse generateAssessment(AssessmentRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable(
                    "AI assessment generation is not configured: the AI_API_KEY environment variable is not set.");
        }

        String outputLanguage = languageName(request.getLanguage());

        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildGenerationPrompt(outputLanguage, request.getQuestionCount())),
                ChatMessage.user(buildGenerationUserPrompt(request)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        AssessmentResponse response = parseAssessmentGeneration(rawContent, request);
        return response;
    }

    @Override
    public AssessmentResult evaluateAssessment(AssessmentSubmissionRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw AiException.unavailable(
                    "AI assessment evaluation is not configured: the AI_API_KEY environment variable is not set.");
        }

        String outputLanguage = languageName(request.getLanguage());

        // Compute one hint per question, aligned by index, so the AI evaluation
        // prompt always sees the question's own result (never a shifted list).
        // MCQ answers with a known correct index are scored by exact match;
        // everything else is evaluated semantically by the AI.
        List<String> perQuestionHints = new ArrayList<>();
        if (request.getQuestions() != null && request.getAnswers() != null) {
            for (int i = 0; i < request.getQuestions().size() && i < request.getAnswers().size(); i++) {
                AssessmentQuestion question = request.getQuestions().get(i);
                AssessmentSubmissionRequest.StudentAnswer answer = request.getAnswers().get(i);

                if ("MCQ".equalsIgnoreCase(question.getType()) && question.getCorrectOptionIndex() != null) {
                    boolean correct = question.getCorrectOptionIndex().equals(answer.getSelectedOptionIndex());
                    perQuestionHints.add(correct ? "CORRECT (exact option match)" : "INCORRECT (selected the wrong option)");
                } else {
                    perQuestionHints.add("SHORT_ANSWER/APPLICATION — evaluate semantically");
                }
            }
        }

        // Use AI to evaluate short answers and generate the full report
        List<ChatMessage> messages = List.of(
                ChatMessage.system(buildEvaluationPrompt(outputLanguage)),
                ChatMessage.user(buildEvaluationUserPrompt(request, perQuestionHints)));

        String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, messages);

        return parseEvaluationResult(rawContent, request);
    }

    /** System prompt for assessment generation. */
    private String buildGenerationPrompt(String outputLanguage, int questionCount) {
        return """
                You are an expert educational assessment designer.

                RULES:
                1. Generate exactly %d questions based on the ACTUAL lesson content provided.
                2. Questions must test understanding, not just memorization.
                3. Use a mixture of question types appropriate for the subject:
                   - MCQ: for concept testing and factual recall
                   - SHORT_ANSWER: for explanation and reasoning
                   - APPLICATION: for applying concepts to new situations
                4. For MCQ: provide exactly 4 options with one correct answer, and set
                   "correctOptionIndex" to the zero-based index of the correct option (0-3).
                5. For SHORT_ANSWER: no options needed, just the question; set "options" to []
                   and "correctOptionIndex" to null.
                6. For APPLICATION: no options needed, question should require applying the concept;
                   set "options" to [] and "correctOptionIndex" to null.
                7. Each question must have a "concept" field indicating what it tests.		        8. Questions must respect the student's educational level and prior knowledge
		           (when prior knowledge is provided, do not test what they already know).
		        9. If the student has limited available time, keep questions focused and
		           avoid overly long application prompts.
		        10. Generate all questions and text in %s.		        11. Do NOT use any previously seen or hardcoded questions — generate fresh ones from the actual lesson.
		        12. Mix question types appropriately — don't make all questions the same type.

                OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
                Use exactly this schema:
                %s
                """.formatted(questionCount, outputLanguage, GENERATION_SCHEMA);
    }

    /** User prompt for assessment generation. */
    private String buildGenerationUserPrompt(AssessmentRequest request) {
        StringBuilder prompt = new StringBuilder();		prompt.append("STUDENT PROFILE:\n");
		prompt.append("Education Level: ").append(request.getEducationLevel()).append('\n');
		prompt.append("Teaching Style: ").append(request.getTeachingStyle()).append('\n');
		prompt.append("Learning Objective: ").append(request.getObjective()).append('\n');
		prompt.append("Language: ").append(request.getLanguage()).append('\n');
		if (request.getPriorKnowledge() != null && !request.getPriorKnowledge().isBlank()) {
			prompt.append("Existing Knowledge: ").append(request.getPriorKnowledge()).append('\n');
		}
		if (request.getAvailableTime() != null && !request.getAvailableTime().isBlank()) {
			prompt.append("Available Study Time: ").append(request.getAvailableTime()).append('\n');
		}
		if (request.getDesiredDepth() != null && !request.getDesiredDepth().isBlank()) {
			prompt.append("Desired Depth: ").append(request.getDesiredDepth()).append('\n');
		}

        prompt.append("\nLESSON CONTENT:\n");
        prompt.append("Title: ").append(request.getLessonTitle()).append('\n');
        prompt.append("Introduction: ").append(request.getIntroduction()).append('\n');

        if (request.getSections() != null) {
            for (int i = 0; i < request.getSections().size(); i++) {
                var section = request.getSections().get(i);
                prompt.append("\nSection ").append(i + 1).append(": ").append(section.getTitle()).append('\n');
                prompt.append("Explanation: ").append(section.getExplanation()).append('\n');
                if (section.getExample() != null && !section.getExample().isBlank()) {
                    prompt.append("Example: ").append(section.getExample()).append('\n');
                }
            }
        }

        if (request.getAdaptiveSummary() != null) {
            var summary = request.getAdaptiveSummary();
            prompt.append("\nADAPTIVE TEACHING DATA:\n");
            prompt.append("Concepts needing re-teaching: ").append(summary.getConceptsNeedingReteaching()).append('\n');
            prompt.append("Misconceptions detected: ").append(summary.getMisconceptionsDetected()).append('\n');
        }

        prompt.append("\nGenerate the assessment questions now.\n");
        return prompt.toString();
    }

    /** System prompt for assessment evaluation. */
    private String buildEvaluationPrompt(String outputLanguage) {
        return """
                You are an expert teacher evaluating a student's final assessment.

                RULES:
                1. Evaluate each answer based on the QUESTION, STUDENT ANSWER, and EXPECTED CONCEPT.
                2. For MCQ: compare the student's selected answer to the correct option.
                3. For SHORT_ANSWER/APPLICATION: evaluate the MEANING of the answer, not string matching.
                4. Count exact number of correct answers for the score.
                5. Calculate percentage as (score / total) * 100.
                6. List concepts the student understood correctly.
                7. List weak areas where the student struggled.
                8. List concepts answered incorrectly.
                9. Generate personalized revision recommendations for each weak area.
                10. Suggest a logical next topic based on:
                    - Current topic
                    - Student's performance
                    - Weak areas
                    - Educational level
                11. Write ALL text in %s.
                12. Do NOT fake the score — count carefully.
                13. The performanceSummary should be a brief, encouraging overall assessment.

                OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary.
                Use exactly this schema:
                %s
                """.formatted(outputLanguage, EVALUATION_SCHEMA);
    }

    /** User prompt for assessment evaluation. */
    private String buildEvaluationUserPrompt(AssessmentSubmissionRequest request,
            List<String> perQuestionHints) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("TOPIC: ").append(request.getTopic()).append('\n');
        prompt.append("LESSON: ").append(request.getLessonTitle()).append('\n');
        prompt.append("EDUCATION LEVEL: ").append(request.getEducationLevel()).append('\n');

        prompt.append("\nQUESTIONS AND ANSWERS:\n");
        if (request.getQuestions() != null && request.getAnswers() != null) {
            for (int i = 0; i < request.getQuestions().size() && i < request.getAnswers().size(); i++) {
                AssessmentQuestion question = request.getQuestions().get(i);
                AssessmentSubmissionRequest.StudentAnswer answer = request.getAnswers().get(i);

                prompt.append("\n--- Question ").append(i + 1).append(" ---\n");
                prompt.append("Type: ").append(question.getType()).append('\n');
                prompt.append("Question: ").append(question.getQuestion()).append('\n');
                prompt.append("Concept Being Tested: ").append(question.getConcept()).append('\n');
                prompt.append("Student Answer: ").append(answer.getAnswer()).append('\n');

                if ("MCQ".equalsIgnoreCase(question.getType())) {
                    prompt.append("MCQ Evaluation: ").append(perQuestionHints.get(i)).append('\n');
                    if (question.getOptions() != null) {
                        prompt.append("Options: ").append(question.getOptions()).append('\n');
                        prompt.append("Correct Option Index: ").append(question.getCorrectOptionIndex()).append('\n');
                    }
                } else {
                    prompt.append("Evaluation: ").append(perQuestionHints.get(i)).append('\n');
                }
            }
        }

        prompt.append("\nEvaluate all answers and provide the final result.\n");
        return prompt.toString();
    }

    /** Parses the AI's assessment generation response. */
    private AssessmentResponse parseAssessmentGeneration(String rawContent, AssessmentRequest request) {
        JsonNode root;
        try {
            root = json.readTree(stripJsonFences(rawContent));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw AiException.invalidResponse("AI returned content that is not a JSON object");
        }

        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray() || questionsNode.size() == 0) {
            throw AiException.invalidResponse("AI response missing questions array or it is empty");
        }

        List<AssessmentQuestion> questions = new ArrayList<>();
        for (JsonNode qNode : questionsNode) {
            String type = requiredText(qNode, "type", "question type");
            String questionText = requiredText(qNode, "question", "question text");
            String concept = clean(qNode.path("concept").asText(null));

            List<String> options = null;
            Integer correctOptionIndex = null;

            if ("MCQ".equalsIgnoreCase(type)) {
                JsonNode optionsNode = qNode.path("options");
                if (optionsNode.isArray() && optionsNode.size() >= 2) {
                    options = new ArrayList<>();
                    for (JsonNode opt : optionsNode) {
                        options.add(opt.asText(""));
                    }
                }
                // Capture the AI-provided correct option index so MCQ answers can
                // be scored by exact match. When the provider omits it (or returns
                // an out-of-range value) the index stays null and evaluation falls
                // back to semantic AI grading for that question.
                JsonNode indexNode = qNode.path("correctOptionIndex");
                if (indexNode.isIntegralNumber() && options != null) {
                    int index = indexNode.asInt();
                    if (index >= 0 && index < options.size()) {
                        correctOptionIndex = index;
                    }
                }
            }

            questions.add(AssessmentQuestion.builder()
                    .type(type)
                    .question(questionText)
                    .options(options)
                    .correctOptionIndex(correctOptionIndex)
                    .concept(concept)
                    .build());
        }

        return AssessmentResponse.builder()
                .assessmentId(UUID.randomUUID().toString())
                .topic(request.getTopic())
                .language(request.getLanguage())
                .educationLevel(request.getEducationLevel())
                .questions(questions)
                .totalQuestions(questions.size())
                .build();
    }

    /** Parses the AI's evaluation response. */
    private AssessmentResult parseEvaluationResult(String rawContent, AssessmentSubmissionRequest request) {
        JsonNode root;
        try {
            root = json.readTree(stripJsonFences(rawContent));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw AiException.invalidResponse("AI returned content that is not a JSON object");
        }

        int score = root.path("score").asInt(0);
        int total = root.path("total").asInt(request.getQuestions() != null ? request.getQuestions().size() : 5);
        double percentage = root.path("percentage").asDouble(0.0);

        List<String> conceptsUnderstood = parseStringList(root.path("conceptsUnderstood"));
        List<String> weakAreas = parseStringList(root.path("weakAreas"));
        List<String> incorrectConcepts = parseStringList(root.path("incorrectConcepts"));
        String suggestedNextTopic = clean(root.path("suggestedNextTopic").asText(null));
        String performanceSummary = clean(root.path("performanceSummary").asText(null));

        List<AssessmentResult.RevisionRecommendation> revisionRecommendations = new ArrayList<>();
        JsonNode recsNode = root.path("revisionRecommendations");
        if (recsNode.isArray()) {
            for (JsonNode recNode : recsNode) {
                String concept = clean(recNode.path("concept").asText(null));
                String recommendation = clean(recNode.path("recommendation").asText(null));
                if (concept != null && recommendation != null) {
                    revisionRecommendations.add(AssessmentResult.RevisionRecommendation.builder()
                            .concept(concept)
                            .recommendation(recommendation)
                            .build());
                }
            }
        }

        return AssessmentResult.builder()
                .score(score)
                .total(total)
                .percentage(percentage)
                .conceptsUnderstood(conceptsUnderstood)
                .weakAreas(weakAreas)
                .incorrectConcepts(incorrectConcepts)
                .revisionRecommendations(revisionRecommendations)
                .suggestedNextTopic(suggestedNextTopic)
                .performanceSummary(performanceSummary)
                .build();
    }

    /** Parses a JSON array of strings. */
    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }
        return result;
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

    /** Maps language codes/names to full language names. */
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
