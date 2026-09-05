package com.aiteacher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;
import com.aiteacher.dto.EvaluationRequest;
import com.aiteacher.dto.EvaluationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AIAnswerEvaluationService}. The HTTP provider is
 * replaced by a fake {@link AiChatClient}, so the tests cover prompt
 * construction, response validation/mapping and error handling — no network,
 * no API key required.
 */
class AIAnswerEvaluationServiceTests {

    private FakeChatClient chatClient;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        this.chatClient = new FakeChatClient();
        this.json = new ObjectMapper();
    }

    private AIAnswerEvaluationService service(String apiKey) {
        return new AIAnswerEvaluationService(chatClient, json, apiKey,
                "https://api.openai.com/v1", "gpt-4o-mini");
    }

    private EvaluationRequest mcqRequest(String studentAnswer, int correctIndex) {
        return EvaluationRequest.builder()
                .question("What is the primary pigment involved in photosynthesis?")
                .questionType("MCQ")
                .studentAnswer(studentAnswer)
                .correctOptionIndex(correctIndex)
                .lessonSection("Photosynthesis is the process by which plants convert sunlight into energy using chlorophyll.")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .build();
    }

    private EvaluationRequest shortAnswerRequest(String studentAnswer) {
        return EvaluationRequest.builder()
                .question("Explain how plants use sunlight.")
                .questionType("SHORT_ANSWER")
                .studentAnswer(studentAnswer)
                .lessonSection("Photosynthesis is the process by which plants convert sunlight into energy. Plants use chlorophyll in their leaves to capture light energy and convert it into chemical energy (glucose).")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .build();
    }

    private static final String CORRECT_EVALUATION = """
            {
              "status": "CORRECT",
              "feedback": "Correct! You understood that chlorophyll is the main pigment for photosynthesis. Chlorophyll absorbs light energy, primarily in the blue and red wavelengths.",
              "expectedConcept": "Chlorophyll is the primary pigment that absorbs light for photosynthesis",
              "confidence": 0.95
            }
            """;

    private static final String INCORRECT_EVALUATION = """
            {
              "status": "INCORRECT",
              "feedback": "Not quite. Hemoglobin is found in blood and carries oxygen, not light energy. The correct answer is chlorophyll, which is found in plant leaves and absorbs sunlight for photosynthesis.",
              "expectedConcept": "Chlorophyll is the primary pigment that absorbs light for photosynthesis",
              "confidence": 0.92
            }
            """;

    private static final String PARTIAL_EVALUATION = """
            {
              "status": "PARTIALLY_CORRECT",
              "feedback": "You're on the right track! You mentioned plants use sunlight, but the key process is photosynthesis, where chlorophyll converts light energy into chemical energy (glucose).",
              "expectedConcept": "Plants convert light energy into chemical energy through photosynthesis",
              "confidence": 0.88
            }
            """;

    @Test
    void evaluatesCorrectMcqAnswer() {
        chatClient.respond(CORRECT_EVALUATION);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));

        assertNotNull(eval);
        assertEquals("CORRECT", eval.getStatus());
        assertTrue(eval.getFeedback().contains("chlorophyll"));
        assertEquals(0.95, eval.getConfidence(), 0.01);
    }

    @Test
    void evaluatesIncorrectMcqAnswer() {
        chatClient.respond(INCORRECT_EVALUATION);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(mcqRequest("Hemoglobin", 0));

        assertNotNull(eval);
        assertEquals("INCORRECT", eval.getStatus());
        assertTrue(eval.getFeedback().contains("Hemoglobin"));
    }

    @Test
    void evaluatesShortAnswerCorrectly() {
        chatClient.respond(CORRECT_EVALUATION);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(
                shortAnswerRequest("Plants use chlorophyll in their leaves to capture sunlight and convert it into glucose through photosynthesis."));

        assertEquals("CORRECT", eval.getStatus());
        assertNotNull(eval.getFeedback());
    }

    @Test
    void evaluatesShortAnswerPartiallyCorrect() {
        chatClient.respond(PARTIAL_EVALUATION);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(
                shortAnswerRequest("Plants use sunlight to make food."));

        assertEquals("PARTIALLY_CORRECT", eval.getStatus());
        assertTrue(eval.getFeedback().contains("right track"));
    }

    @Test
    void evaluatesShortAnswerIncorrectly() {
        chatClient.respond(INCORRECT_EVALUATION);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(
                shortAnswerRequest("Plants absorb water through their roots to grow."));

        assertEquals("INCORRECT", eval.getStatus());
    }

    @Test
    void mcqPromptContainsQuestionContext() {
        chatClient.respond(CORRECT_EVALUATION);
        service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));

        ChatMessage userMessage = chatClient.lastUserMessage();
        assertNotNull(userMessage);
        String prompt = userMessage.content();
        assertTrue(prompt.contains("What is the primary pigment"));
        assertTrue(prompt.contains("Chlorophyll"));
        assertTrue(prompt.contains("Photosynthesis"));
        assertTrue(prompt.contains("High School"));
    }

    @Test
    void shortAnswerPromptContainsLessonSection() {
        chatClient.respond(CORRECT_EVALUATION);
        service("sk-test-123").evaluateAnswer(
                shortAnswerRequest("Plants use chlorophyll to capture sunlight."));

        ChatMessage userMessage = chatClient.lastUserMessage();
        String prompt = userMessage.content();
        assertTrue(prompt.contains("Explain how plants use sunlight"));
        assertTrue(prompt.contains("chlorophyll"));
        assertTrue(prompt.contains("Photosynthesis"));
    }

    @Test
    void promptRequestsEnglishFeedback() {
        chatClient.respond(CORRECT_EVALUATION);
        service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in English"));
    }

    @Test
    void promptRequestsHindiFeedback() {
        chatClient.respond(CORRECT_EVALUATION);
        EvaluationRequest hindiRequest = EvaluationRequest.builder()
                .question("प्रकाश संश्लेषण में मुख्य पिग्मेंट क्या है?")
                .questionType("MCQ")
                .studentAnswer("क्लोरोफिल")
                .correctOptionIndex(0)
                .lessonSection("प्रकाश संश्लेषण वह प्रक्रिया है जिससे पौधे सूर्य के प्रकाश को ऊर्जा में बदलते हैं।")
                .topic("प्रकाश संश्लेषण")
                .language("Hindi")
                .educationLevel("School")
                .build();
        service("sk-test-123").evaluateAnswer(hindiRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Hindi"));
    }

    @Test
    void missingApiKeyFailsAsUnavailable() {
        AiException ex = assertThrows(AiException.class,
                () -> service("   ").evaluateAnswer(mcqRequest("Chlorophyll", 0)));
        assertTrue(ex.isUnavailable());
        assertEquals(0, chatClient.callCount);
    }

    @Test
    void upstreamProviderFailurePropagates() {
        chatClient.failWith(AiException.upstream("simulated HTTP 429"));
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0)));
        assertEquals(AiException.Kind.UPSTREAM, ex.getKind());
    }

    @Test
    void nonJsonAiContentIsRejected() {
        chatClient.respond("Sorry, I cannot evaluate that.");
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0)));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @Test
    void missingStatusFieldIsRejected() {
        chatClient.respond("""
                {
                  "feedback": "Good answer.",
                  "expectedConcept": "concept",
                  "confidence": 0.9
                }
                """);
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0)));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
        assertTrue(ex.getMessage().contains("status"));
    }

    @Test
    void confidenceFallsBackToDefaultWhenMissing() {
        chatClient.respond("""
                {
                  "status": "CORRECT",
                  "feedback": "Good answer.",
                  "expectedConcept": "concept"
                }
                """);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));
        assertEquals(0.8, eval.getConfidence(), 0.01);
    }

    @Test
    void outOfRangeConfidenceFallsBackToDefault() {
        chatClient.respond("""
                {
                  "status": "CORRECT",
                  "feedback": "Good answer.",
                  "expectedConcept": "concept",
                  "confidence": 5.0
                }
                """);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));
        assertEquals(0.8, eval.getConfidence(), 0.01);
    }

    @Test
    void markdownFencedJsonIsStillParsed() {
        chatClient.respond("```json\n" + CORRECT_EVALUATION + "\n```");
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));
        assertEquals("CORRECT", eval.getStatus());
    }

    @Test
    void statusNormalizationHandlesVariations() {
        chatClient.respond("""
                {
                  "status": "partially correct",
                  "feedback": "Almost there.",
                  "expectedConcept": "concept",
                  "confidence": 0.85
                }
                """);
        EvaluationResponse eval = service("sk-test-123").evaluateAnswer(
                shortAnswerRequest("Plants use sunlight somehow."));
        assertEquals("PARTIALLY_CORRECT", eval.getStatus());
    }

    @Test
    void endpointAndModelArePassedToClient() {
        chatClient.respond(CORRECT_EVALUATION);
        service("sk-test-123").evaluateAnswer(mcqRequest("Chlorophyll", 0));
        assertEquals("https://api.openai.com/v1", chatClient.lastBaseUrl);
        assertEquals("sk-test-123", chatClient.lastApiKey);
        assertEquals("gpt-4o-mini", chatClient.lastModel);
    }

    /** Fake transport: returns a canned body or throws, and records the request. */
    private static final class FakeChatClient implements AiChatClient {

        private String responseBody;
        private AiException failure;
        private String lastBaseUrl;
        private String lastApiKey;
        private String lastModel;
        private List<ChatMessage> lastMessages;
        private int callCount;

        void respond(String body) {
            this.responseBody = body;
            this.failure = null;
        }

        void failWith(AiException ex) {
            this.failure = ex;
            this.responseBody = null;
        }

        ChatMessage lastSystemMessage() {
            return lastMessages.stream().filter(m -> m.role().equals("system")).findFirst().orElse(null);
        }

        ChatMessage lastUserMessage() {
            return lastMessages.stream().filter(m -> m.role().equals("user")).findFirst().orElse(null);
        }

        @Override
        public String chatCompletion(String baseUrl, String apiKey, String model, List<ChatMessage> messages) {
            this.callCount++;
            this.lastBaseUrl = baseUrl;
            this.lastApiKey = apiKey;
            this.lastModel = model;
            this.lastMessages = messages;
            if (failure != null) {
                throw failure;
            }
            return responseBody;
        }
    }
}
