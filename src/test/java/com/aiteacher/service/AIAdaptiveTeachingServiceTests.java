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
import com.aiteacher.dto.AdaptiveTeachingRequest;
import com.aiteacher.dto.AdaptiveTeachingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AIAdaptiveTeachingService}. The HTTP provider is
 * replaced by a fake {@link AiChatClient}, so the tests cover prompt
 * construction, response validation/mapping and error handling — no network,
 * no API key required.
 */
class AIAdaptiveTeachingServiceTests {

    private FakeChatClient chatClient;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        this.chatClient = new FakeChatClient();
        this.json = new ObjectMapper();
    }

    private AIAdaptiveTeachingService service(String apiKey) {
        return new AIAdaptiveTeachingService(chatClient, json, apiKey,
                "https://api.openai.com/v1", "gpt-4o-mini");
    }

    private AdaptiveTeachingRequest basicRequest() {
        return AdaptiveTeachingRequest.builder()
                .understanding("MISCONCEPTION")
                .misconception("Student confused chlorophyll with hemoglobin")
                .recommendedApproach("ANALOGY")
                .originalExplanation("Photosynthesis uses chlorophyll to capture light energy.")
                .originalExample("Leaves use chlorophyll to capture sunlight.")
                .sectionTitle("What is Photosynthesis")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .teachingStyle("Example-Based")
                .objective("Understand the basics")
                .studentAnswer("Hemoglobin")
                .question("What is the primary pigment?")
                .adaptationCount(0)
                .build();
    }

    private static final String VALID_ADAPTATION_JSON = """
            {
              "adaptationType": "ANALOGY",
              "misconception": "Student confused chlorophyll with hemoglobin",
              "reExplanation": "Think of chlorophyll like a solar panel on a leaf. Just like a solar panel captures sunlight and converts it to electricity, chlorophyll captures sunlight and converts it to food energy for the plant.",
              "example": "Imagine your phone's solar charger. It absorbs sunlight and turns it into battery power. Chlorophyll does the same thing inside plant cells — it's nature's solar panel!",
              "followUpQuestion": "If a plant had no chlorophyll, what would happen to its ability to make food?",
              "followUpQuestionType": "MCQ",
              "followUpOptions": ["It would still make food normally", "It would lose the ability to capture sunlight for food", "It would make more food", "It would only work at night"],
              "followUpCorrectOptionIndex": 1
            }
            """;

    private static final String SHORT_ANSWER_FOLLOWUP_JSON = """
            {
              "adaptationType": "STEP_BY_STEP",
              "misconception": "Student doesn't understand the concept",
              "reExplanation": "Let me break this down step by step.",
              "example": "Here is a simple example.",
              "followUpQuestion": "Explain in your own words how plants use sunlight.",
              "followUpQuestionType": "SHORT_ANSWER",
              "followUpOptions": [],
              "followUpCorrectOptionIndex": null
            }
            """;

    @Test
    void generatesMcqFollowUpQuestion() {
        chatClient.respond(VALID_ADAPTATION_JSON);
        AdaptiveTeachingResponse response = service("sk-test-123").generateAdaptation(basicRequest());

        assertNotNull(response);
        assertEquals("ANALOGY", response.getAdaptationType());
        assertNotNull(response.getReExplanation());
        assertTrue(response.getReExplanation().contains("solar panel"));
        assertNotNull(response.getExample());
        assertEquals("MCQ", response.getFollowUpQuestionType());
        assertEquals(4, response.getFollowUpOptions().size());
        assertEquals(1, response.getFollowUpCorrectOptionIndex());
    }

    @Test
    void generatesShortAnswerFollowUpQuestion() {
        chatClient.respond(SHORT_ANSWER_FOLLOWUP_JSON);
        AdaptiveTeachingResponse response = service("sk-test-123").generateAdaptation(basicRequest());

        assertEquals("STEP_BY_STEP", response.getAdaptationType());
        assertEquals("SHORT_ANSWER", response.getFollowUpQuestionType());
        assertTrue(response.getFollowUpOptions().isEmpty());
        assertFalse(response.getReExplanation().contains("solar panel"));
    }

    @Test
    void promptCarriesMisconceptionAndOriginalContent() {
        chatClient.respond(VALID_ADAPTATION_JSON);
        service("sk-test-123").generateAdaptation(basicRequest());

        ChatMessage userMessage = chatClient.lastUserMessage();
        assertNotNull(userMessage);
        String prompt = userMessage.content();
        assertTrue(prompt.contains("MISCONCEPTION"));
        assertTrue(prompt.contains("chlorophyll"));
        assertTrue(prompt.contains("hemoglobin"));
        assertTrue(prompt.contains("ANALOGY"));
        assertTrue(prompt.contains("Photosynthesis uses chlorophyll"));
        assertTrue(prompt.contains("High School"));
        assertTrue(prompt.contains("Example-Based"));
        assertTrue(prompt.contains("What is the primary pigment?"));
        assertTrue(prompt.contains("Hemoglobin"));
        assertTrue(prompt.contains("1")); // adaptationCount + 1
    }

    @Test
    void promptRequestsEnglishExplanation() {
        chatClient.respond(VALID_ADAPTATION_JSON);
        service("sk-test-123").generateAdaptation(basicRequest());

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in English"));
    }

    @Test
    void promptRequestsHindiExplanation() {
        chatClient.respond(VALID_ADAPTATION_JSON);
        AdaptiveTeachingRequest hindiRequest = AdaptiveTeachingRequest.builder()
                .understanding("MISCONCEPTION")
                .misconception("विद्यार्थी ने क्लोरोफिल को हीमोग्लोबिन समझा")
                .recommendedApproach("ANALOGY")
                .originalExplanation("प्रकाश संश्लेषण क्लोरोफिल का उपयोग करता है।")
                .originalExample("पत्तियां क्लोरोफिल का उपयोग करती हैं।")
                .sectionTitle("प्रकाश संश्लेषण क्या है")
                .topic("प्रकाश संश्लेषण")
                .language("Hindi")
                .educationLevel("School")
                .teachingStyle("Simple Explanation")
                .objective("बुनियादी बातें समझना")
                .studentAnswer("हीमोग्लोबिन")
                .question("मुख्य पिग्मेंट क्या है?")
                .adaptationCount(0)
                .build();
        service("sk-test-123").generateAdaptation(hindiRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Hindi"));
    }

    @Test
    void promptRequestsKannadaExplanation() {
        chatClient.respond(VALID_ADAPTATION_JSON);
        AdaptiveTeachingRequest kannadaRequest = AdaptiveTeachingRequest.builder()
                .understanding("MISCONCEPTION")
                .misconception("ವಿದ್ಯಾರ್ಥಿ ಕ್ಲೋರೋಫಿಲ್ ಅನ್ನು ಹಿಮೋಗ್ಲೋಬಿನ್ ಎಂದು ತಪ್ಪಾಗಿ ಗ್ರಹಿಸಿದ್ದಾನೆ")
                .recommendedApproach("ANALOGY")
                .originalExplanation("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ ಕ್ಲೋರೋಫಿಲ್ ಬಳಸುತ್ತದೆ।")
                .originalExample("ಎಲೆಗಳು ಕ್ಲೋರೋಫಿಲ್ ಬಳಸುತ್ತವೆ।")
                .sectionTitle("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ ಎಂದರೇನು")
                .topic("ದ್ಯುತಿಸಂಶ್ಲೇಷಣೆ")
                .language("Kannada")
                .educationLevel("College")
                .teachingStyle("Visual")
                .objective("ಆಧಾರಭೂತ ತಿಳುವಳಿಕೆ")
                .studentAnswer("ಹಿಮೋಗ್ಲೋಬಿನ್")
                .question("ಪ್ರಾಥಮಿಕ ವರ್ಣದ್ರವ್ಯ ಯಾವುದು?")
                .adaptationCount(0)
                .build();
        service("sk-test-123").generateAdaptation(kannadaRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Kannada"));
    }

    @Test
    void missingApiKeyFailsAsUnavailable() {
        AiException ex = assertThrows(AiException.class,
                () -> service("   ").generateAdaptation(basicRequest()));
        assertTrue(ex.isUnavailable());
        assertEquals(0, chatClient.callCount);
    }

    @Test
    void upstreamProviderFailurePropagates() {
        chatClient.failWith(AiException.upstream("simulated HTTP 429"));
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateAdaptation(basicRequest()));
        assertEquals(AiException.Kind.UPSTREAM, ex.getKind());
    }

    @Test
    void nonJsonAiContentIsRejected() {
        chatClient.respond("Sorry, I cannot help with that.");
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateAdaptation(basicRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @Test
    void mcqWithWrongOptionCountFallsBackToShortAnswer() {
        chatClient.respond("""
                {
                  "adaptationType": "EXAMPLE_BASED",
                  "misconception": "test",
                  "reExplanation": "Here is a new explanation.",
                  "example": "Example",
                  "followUpQuestion": "Test question?",
                  "followUpQuestionType": "MCQ",
                  "followUpOptions": ["A", "B"],
                  "followUpCorrectOptionIndex": 0
                }
                """);
        AdaptiveTeachingResponse response = service("sk-test-123").generateAdaptation(basicRequest());
        assertEquals("SHORT_ANSWER", response.getFollowUpQuestionType());
    }

    @Test
    void emptyFollowUpQuestionStillReturnsExplanation() {
        chatClient.respond("""
                {
                  "adaptationType": "SIMPLER",
                  "misconception": "test",
                  "reExplanation": "Simpler explanation here.",
                  "example": "Example",
                  "followUpQuestion": "Got it?",
                  "followUpQuestionType": "SHORT_ANSWER"
                }
                """);
        AdaptiveTeachingResponse response = service("sk-test-123").generateAdaptation(basicRequest());
        assertNotNull(response.getReExplanation());
        assertEquals("SIMPLER", response.getAdaptationType());
    }

    @Test
    void markdownFencedJsonIsStillParsed() {
        chatClient.respond("```json\n" + VALID_ADAPTATION_JSON + "\n```");
        AdaptiveTeachingResponse response = service("sk-test-123").generateAdaptation(basicRequest());
        assertEquals("ANALOGY", response.getAdaptationType());
    }

    @Test
    void endpointAndModelArePassedToClient() {
        chatClient.respond(VALID_ADAPTATION_JSON);
        service("sk-test-123").generateAdaptation(basicRequest());
        assertEquals("https://api.openai.com/v1", chatClient.lastBaseUrl);
        assertEquals("sk-test-123", chatClient.lastApiKey);
        assertEquals("gpt-4o-mini", chatClient.lastModel);
    }

    @Test
    void adaptationCountIsIncludedInPrompt() {
        AdaptiveTeachingRequest requestWithCount = AdaptiveTeachingRequest.builder()
                .understanding("MISCONCEPTION")
                .misconception("test misconception")
                .recommendedApproach("SIMPLER")
                .originalExplanation("Original explanation")
                .sectionTitle("Section")
                .topic("Topic")
                .language("English")
                .educationLevel("College")
                .teachingStyle("Step-by-Step")
                .objective("Learn something")
                .studentAnswer("wrong answer")
                .question("What is X?")
                .adaptationCount(1)
                .build();
        chatClient.respond(VALID_ADAPTATION_JSON);
        service("sk-test-123").generateAdaptation(requestWithCount);

        String prompt = chatClient.lastUserMessage().content();
        assertTrue(prompt.contains("2")); // adaptationCount + 1
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
