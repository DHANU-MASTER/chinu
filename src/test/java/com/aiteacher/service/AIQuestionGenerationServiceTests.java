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
import com.aiteacher.dto.QuestionRequest;
import com.aiteacher.dto.QuestionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AIQuestionGenerationService}. The HTTP provider is
 * replaced by a fake {@link AiChatClient}, so the tests cover prompt
 * construction, response validation/mapping and error handling — no network,
 * no API key required.
 */
class AIQuestionGenerationServiceTests {

    private FakeChatClient chatClient;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        this.chatClient = new FakeChatClient();
        this.json = new ObjectMapper();
    }

    private AIQuestionGenerationService service(String apiKey) {
        return new AIQuestionGenerationService(chatClient, json, apiKey,
                "https://api.openai.com/v1", "gpt-4o-mini");
    }

    private QuestionRequest basicRequest() {
        return QuestionRequest.builder()
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .teachingStyle("Simple Explanation")
                .objective("Understand the basics")
                .sectionTitle("What is Photosynthesis")
                .sectionContent("Photosynthesis is the process by which plants convert sunlight into energy.")
                .sectionExample("For example, leaves use chlorophyll to capture light energy.")
                .build();
    }

    private static final String VALID_MCQ_JSON = """
            {
              "type": "MCQ",
              "question": "What is the primary pigment involved in photosynthesis?",
              "options": ["Chlorophyll", "Hemoglobin", "Melanin", "Carotene"],
              "correctOptionIndex": 0,
              "expectedConcept": "Understanding that chlorophyll is the main pigment for photosynthesis"
            }
            """;

    private static final String VALID_SHORT_ANSWER_JSON = """
            {
              "type": "SHORT_ANSWER",
              "question": "Explain in your own words how plants use sunlight.",
              "options": [],
              "correctOptionIndex": null,
              "expectedConcept": "Understanding that plants convert light energy into chemical energy through photosynthesis"
            }
            """;

    @Test
    void streamsQuestionDeltasAndParsesSameQuestion() {
        chatClient.respond(VALID_MCQ_JSON);

        StringBuilder receivedDeltas = new StringBuilder();
        QuestionResponse question = service("sk-test-123")
                .generateQuestionStreaming(basicRequest(), receivedDeltas::append);

        assertEquals(VALID_MCQ_JSON, receivedDeltas.toString());
        assertEquals("MCQ", question.getType());
        assertEquals(4, question.getOptions().size());
    }

    @Test
    void generatesMcqFromLessonContent() {
        chatClient.respond(VALID_MCQ_JSON);
        QuestionResponse question = service("sk-test-123").generateQuestion(basicRequest());

        assertNotNull(question);
        assertEquals("MCQ", question.getType());
        assertEquals("What is the primary pigment involved in photosynthesis?", question.getQuestion());
        assertEquals(4, question.getOptions().size());
        assertEquals(0, question.getCorrectOptionIndex());
        assertNotNull(question.getQuestionId());
    }

    @Test
    void generatesShortAnswerFromLessonContent() {
        chatClient.respond(VALID_SHORT_ANSWER_JSON);
        QuestionResponse question = service("sk-test-123").generateQuestion(basicRequest());

        assertNotNull(question);
        assertEquals("SHORT_ANSWER", question.getType());
        assertEquals("Explain in your own words how plants use sunlight.", question.getQuestion());
        assertTrue(question.getOptions().isEmpty());
        assertNotNull(question.getExpectedConcept());
    }

    @Test
    void promptCarriesSectionContent() {
        chatClient.respond(VALID_MCQ_JSON);
        service("sk-test-123").generateQuestion(basicRequest());

        ChatMessage userMessage = chatClient.lastUserMessage();
        assertNotNull(userMessage);
        String prompt = userMessage.content();
        assertTrue(prompt.contains("Topic: Photosynthesis"));
        assertTrue(prompt.contains("Title: What is Photosynthesis"));
        assertTrue(prompt.contains("Photosynthesis is the process"));
        assertTrue(prompt.contains("chlorophyll to capture light energy"));
        assertTrue(prompt.contains("High School"));
    }

    @Test
    void promptRequestsEnglishWhenLanguageIsEnglish() {
        chatClient.respond(VALID_MCQ_JSON);
        service("sk-test-123").generateQuestion(basicRequest());

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in English"));
    }

    @Test
    void promptRequestsHindiWhenLanguageIsHindi() {
        chatClient.respond(VALID_MCQ_JSON);
        QuestionRequest hindiRequest = QuestionRequest.builder()
                .topic("प्रकाश संश्लेषण")
                .language("Hindi")
                .educationLevel("School")
                .teachingStyle("Simple Explanation")
                .objective("बुनियादी बातें समझना")
                .sectionTitle("प्रकाश संश्लेषण क्या है")
                .sectionContent("प्रकाश संश्लेषण वह प्रक्रिया है जिससे पौधे सूर्य के प्रकाश को ऊर्जा में बदलते हैं।")
                .build();
        service("sk-test-123").generateQuestion(hindiRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Hindi"));
    }

    @Test
    void promptRequestsKannadaWhenLanguageIsKannada() {
        chatClient.respond(VALID_MCQ_JSON);
        QuestionRequest kannadaRequest = QuestionRequest.builder()
                .topic("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ")
                .language("Kannada")
                .educationLevel("College")
                .teachingStyle("Visual")
                .objective("ಆಧಾರಭೂತ ತಿಳುವಳಿಕೆ")
                .sectionTitle("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ ಎಂದರೇನು")
                .sectionContent("ಸಸ್ಯಗಳು ಸೂರ್ಯನ ಬೆಳಕನ್ನು ಶಕ್ತಿಯಾಗಿ ಪರಿವರ್ತಿಸುವ ಪ್ರಕ್ರಿಯೆ.")
                .build();
        service("sk-test-123").generateQuestion(kannadaRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Kannada"));
    }

    @Test
    void missingApiKeyFailsAsUnavailable() {
        AiException ex = assertThrows(AiException.class,
                () -> service("   ").generateQuestion(basicRequest()));
        assertTrue(ex.isUnavailable());
        assertTrue(ex.getMessage().contains("AI_API_KEY"));
        assertEquals(0, chatClient.callCount);
    }

    @Test
    void upstreamProviderFailurePropagates() {
        chatClient.failWith(AiException.upstream("simulated HTTP 429"));
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateQuestion(basicRequest()));
        assertEquals(AiException.Kind.UPSTREAM, ex.getKind());
    }

    @Test
    void nonJsonAiContentIsRejected() {
        chatClient.respond("Sorry, I cannot help with that.");
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateQuestion(basicRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @Test
    void mcqWithWrongOptionCountIsRejected() {
        chatClient.respond("""
                {
                  "type": "MCQ",
                  "question": "Test?",
                  "options": ["A", "B"],
                  "correctOptionIndex": 0,
                  "expectedConcept": "concept"
                }
                """);
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateQuestion(basicRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
        assertTrue(ex.getMessage().contains("4 options"));
    }

    @Test
    void mcqWithInvalidCorrectIndexIsRejected() {
        chatClient.respond("""
                {
                  "type": "MCQ",
                  "question": "Test?",
                  "options": ["A", "B", "C", "D"],
                  "correctOptionIndex": 5,
                  "expectedConcept": "concept"
                }
                """);
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateQuestion(basicRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
        assertTrue(ex.getMessage().contains("0-3"));
    }

    @Test
    void markdownFencedJsonIsStillParsed() {
        chatClient.respond("```json\n" + VALID_MCQ_JSON + "\n```");
        QuestionResponse question = service("sk-test-123").generateQuestion(basicRequest());
        assertEquals("MCQ", question.getType());
        assertEquals(4, question.getOptions().size());
    }

    @Test
    void endpointAndModelArePassedToClient() {
        chatClient.respond(VALID_MCQ_JSON);
        service("sk-test-123").generateQuestion(basicRequest());
        assertEquals("https://api.openai.com/v1", chatClient.lastBaseUrl);
        assertEquals("sk-test-123", chatClient.lastApiKey);
        assertEquals("gpt-4o-mini", chatClient.lastModel);
    }

    @Test
    void questionIdIsGenerated() {
        chatClient.respond(VALID_MCQ_JSON);
        QuestionResponse q1 = service("sk-test-123").generateQuestion(basicRequest());
        chatClient.respond(VALID_MCQ_JSON);
        QuestionResponse q2 = service("sk-test-123").generateQuestion(basicRequest());
        assertNotNull(q1.getQuestionId());
        assertNotNull(q2.getQuestionId());
        // Each question should have a unique ID
        // (UUID.randomUUID() guarantees uniqueness in practice)
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

        @Override
        public void streamChatCompletion(String baseUrl, String apiKey, String model,
                List<ChatMessage> messages, TokenListener listener) {
            this.lastBaseUrl = baseUrl;
            this.lastApiKey = apiKey;
            this.lastModel = model;
            this.lastMessages = messages;
            if (failure != null) {
                throw failure;
            }
            int half = Math.max(1, responseBody.length() / 2);
            listener.onDelta(responseBody.substring(0, half));
            listener.onDelta(responseBody.substring(half));
        }
    }
}
