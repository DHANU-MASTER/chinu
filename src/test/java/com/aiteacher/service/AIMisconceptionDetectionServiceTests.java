package com.aiteacher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;
import com.aiteacher.dto.MisconceptionRequest;
import com.aiteacher.dto.MisconceptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AIMisconceptionDetectionService}. The HTTP provider is
 * replaced by a fake {@link AiChatClient}, so the tests cover prompt
 * construction, response validation/mapping and error handling — no network,
 * no API key required.
 */
class AIMisconceptionDetectionServiceTests {

    private FakeChatClient chatClient;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        this.chatClient = new FakeChatClient();
        this.json = new ObjectMapper();
    }

    private AIMisconceptionDetectionService service(String apiKey) {
        return new AIMisconceptionDetectionService(chatClient, json, apiKey,
                "https://api.openai.com/v1", "gpt-4o-mini");
    }

    private MisconceptionRequest basicRequest() {
        return MisconceptionRequest.builder()
                .question("What is the primary pigment involved in photosynthesis?")
                .questionType("MCQ")
                .studentAnswer("Hemoglobin")
                .correctConcept("Chlorophyll is the primary pigment")
                .evaluationStatus("INCORRECT")
                .evaluationFeedback("Not quite. Hemoglobin is found in blood.")
                .lessonSection("Photosynthesis uses chlorophyll to capture light energy.")
                .sectionTitle("What is Photosynthesis")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .teachingStyle("Example-Based")
                .objective("Understand the basics")
                .build();
    }

    private static final String UNDERSTOOD_JSON = """
            {
              "understanding": "UNDERSTOOD",
              "misconception": "",
              "severity": "LOW",
              "explanationNeeded": false,
              "recommendedApproach": "EXAMPLE_BASED"
            }
            """;

    private static final String MISCONCEPTION_JSON = """
            {
              "understanding": "MISCONCEPTION",
              "misconception": "The student confused chlorophyll (plant pigment) with hemoglobin (blood protein).",
              "severity": "MEDIUM",
              "explanationNeeded": true,
              "recommendedApproach": "ANALOGY"
            }
            """;

    private static final String PARTIAL_JSON = """
            {
              "understanding": "PARTIAL",
              "misconception": "Student knows plants use light but doesn't understand the specific role of chlorophyll.",
              "severity": "LOW",
              "explanationNeeded": true,
              "recommendedApproach": "SIMPLER"
            }
            """;

    private static final String NOT_UNDERSTOOD_JSON = """
            {
              "understanding": "NOT_UNDERSTOOD",
              "misconception": "Student seems confused about the basic concept of how plants get energy.",
              "severity": "HIGH",
              "explanationNeeded": true,
              "recommendedApproach": "STEP_BY_STEP"
            }
            """;

    @Test
    void detectsUnderstandingWhenCorrect() {
        chatClient.respond(UNDERSTOOD_JSON);
        MisconceptionResponse response = service("sk-test-123").detectMisconception(basicRequest());

        assertNotNull(response);
        assertEquals("UNDERSTOOD", response.getUnderstanding());
        assertFalse(response.isExplanationNeeded());
    }

    @Test
    void detectsMisconception() {
        chatClient.respond(MISCONCEPTION_JSON);
        MisconceptionResponse response = service("sk-test-123").detectMisconception(basicRequest());

        assertEquals("MISCONCEPTION", response.getUnderstanding());
        assertTrue(response.getMisconception().contains("chlorophyll"));
        assertTrue(response.isExplanationNeeded());
        assertEquals("MEDIUM", response.getSeverity());
        assertEquals("ANALOGY", response.getRecommendedApproach());
    }

    @Test
    void detectsPartialUnderstanding() {
        chatClient.respond(PARTIAL_JSON);
        MisconceptionResponse response = service("sk-test-123").detectMisconception(basicRequest());

        assertEquals("PARTIAL", response.getUnderstanding());
        assertTrue(response.isExplanationNeeded());
        assertEquals("SIMPLER", response.getRecommendedApproach());
    }

    @Test
    void detectsNotUnderstood() {
        chatClient.respond(NOT_UNDERSTOOD_JSON);
        MisconceptionResponse response = service("sk-test-123").detectMisconception(basicRequest());

        assertEquals("NOT_UNDERSTOOD", response.getUnderstanding());
        assertTrue(response.isExplanationNeeded());
        assertEquals("HIGH", response.getSeverity());
        assertEquals("STEP_BY_STEP", response.getRecommendedApproach());
    }

    @Test
    void promptCarriesFullContext() {
        chatClient.respond(UNDERSTOOD_JSON);
        service("sk-test-123").detectMisconception(basicRequest());

        ChatMessage userMessage = chatClient.lastUserMessage();
        assertNotNull(userMessage);
        String prompt = userMessage.content();
        assertTrue(prompt.contains("What is the primary pigment"));
        assertTrue(prompt.contains("Hemoglobin"));
        assertTrue(prompt.contains("Chlorophyll is the primary pigment"));
        assertTrue(prompt.contains("INCORRECT"));
        assertTrue(prompt.contains("High School"));
        assertTrue(prompt.contains("Example-Based"));
        assertTrue(prompt.contains("Photosynthesis"));
    }

    @Test
    void promptRequestsEnglishAnalysis() {
        chatClient.respond(UNDERSTOOD_JSON);
        service("sk-test-123").detectMisconception(basicRequest());

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in English"));
    }

    @Test
    void promptRequestsHindiAnalysis() {
        chatClient.respond(UNDERSTOOD_JSON);
        MisconceptionRequest hindiRequest = MisconceptionRequest.builder()
                .question("प्रकाश संश्लेषण में मुख्य पिग्मेंट क्या है?")
                .questionType("MCQ")
                .studentAnswer("हीमोग्लोबिन")
                .correctConcept("क्लोरोफिल मुख्य पिग्मेंट है")
                .evaluationStatus("INCORRECT")
                .evaluationFeedback("नहीं, हीमोग्लोबिन रक्त में होता है")
                .lessonSection("प्रकाश संश्लेषण क्लोरोफिल का उपयोग करता है")
                .sectionTitle("प्रकाश संश्लेषण क्या है")
                .topic("प्रकाश संश्लेषण")
                .language("Hindi")
                .educationLevel("School")
                .teachingStyle("Simple Explanation")
                .objective("बुनियादी बातें समझना")
                .build();
        service("sk-test-123").detectMisconception(hindiRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Hindi"));
    }

    @Test
    void promptRequestsKannadaAnalysis() {
        chatClient.respond(UNDERSTOOD_JSON);
        MisconceptionRequest kannadaRequest = MisconceptionRequest.builder()
                .question("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆಯಲ್ಲಿ ಪ್ರಾಥಮಿಕ ವರ್ಣದ್ರವ್ಯ ಯಾವುದು?")
                .questionType("MCQ")
                .studentAnswer("ಹಿಮೋಗ್ಲೋಬಿನ್")
                .correctConcept("ಕ್ಲೋರೋಫಿಲ್ ಪ್ರಾಥಮಿಕ ವರ್ಣದ್ರವ್ಯ")
                .evaluationStatus("INCORRECT")
                .evaluationFeedback("ತಪ್ಪು, ಹಿಮೋಗ್ಲೋಬಿನ್ ರಕ್ತದಲ್ಲಿದೆ")
                .lessonSection("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ ಕ್ಲೋರೋಫಿಲ್ ಬಳಸುತ್ತದೆ")
                .sectionTitle("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ ಎಂದರೇನು")
                .topic("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ")
                .language("Kannada")
                .educationLevel("College")
                .teachingStyle("Visual")
                .objective("ಆಧಾರಭೂತ ತಿಳುವಳಿಕೆ")
                .build();
        service("sk-test-123").detectMisconception(kannadaRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Kannada"));
    }

    @Test
    void missingApiKeyFailsAsUnavailable() {
        AiException ex = assertThrows(AiException.class,
                () -> service("   ").detectMisconception(basicRequest()));
        assertTrue(ex.isUnavailable());
        assertEquals(0, chatClient.callCount);
    }

    @Test
    void upstreamProviderFailurePropagates() {
        chatClient.failWith(AiException.upstream("simulated HTTP 429"));
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").detectMisconception(basicRequest()));
        assertEquals(AiException.Kind.UPSTREAM, ex.getKind());
    }

    @Test
    void nonJsonAiContentIsRejected() {
        chatClient.respond("Sorry, I cannot analyze that.");
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").detectMisconception(basicRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @Test
    void missingUnderstandingFieldIsRejected() {
        chatClient.respond("""
                {
                  "misconception": "test",
                  "severity": "LOW",
                  "explanationNeeded": false,
                  "recommendedApproach": "SIMPLER"
                }
                """);
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").detectMisconception(basicRequest()));
        assertTrue(ex.getMessage().contains("understanding"));
    }

    @Test
    void severityFallsBackToMediumWhenMissing() {
        chatClient.respond("""
                {
                  "understanding": "UNDERSTOOD",
                  "explanationNeeded": false,
                  "recommendedApproach": "SIMPLER"
                }
                """);
        MisconceptionResponse response = service("sk-test-123").detectMisconception(basicRequest());
        assertEquals("MEDIUM", response.getSeverity());
    }

    @Test
    void markdownFencedJsonIsStillParsed() {
        chatClient.respond("```json\n" + UNDERSTOOD_JSON + "\n```");
        MisconceptionResponse response = service("sk-test-123").detectMisconception(basicRequest());
        assertEquals("UNDERSTOOD", response.getUnderstanding());
    }

    @Test
    void endpointAndModelArePassedToClient() {
        chatClient.respond(UNDERSTOOD_JSON);
        service("sk-test-123").detectMisconception(basicRequest());
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
