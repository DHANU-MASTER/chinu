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
import com.aiteacher.dto.AssessmentQuestion;
import com.aiteacher.dto.AssessmentRequest;
import com.aiteacher.dto.AssessmentResponse;
import com.aiteacher.dto.AssessmentResult;
import com.aiteacher.dto.AssessmentSubmissionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AIAssessmentService}. The HTTP provider is
 * replaced by a fake {@link AiChatClient}, so the tests cover prompt
 * construction, response validation/mapping and error handling — no network,
 * no API key required.
 */
class AIAssessmentServiceTests {

    private FakeChatClient chatClient;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        this.chatClient = new FakeChatClient();
        this.json = new ObjectMapper();
    }

    private AIAssessmentService service(String apiKey) {
        return new AIAssessmentService(chatClient, json, apiKey,
                "https://api.openai.com/v1", "gpt-4o-mini");
    }

    private AssessmentRequest sampleRequest() {
        return AssessmentRequest.builder()
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .teachingStyle("Example-Based")
                .objective("Understand how plants convert sunlight into energy")
                .lessonTitle("Introduction to Photosynthesis")
                .introduction("Photosynthesis is the process by which plants convert sunlight into energy.")
                .sections(List.of(
                        AssessmentRequest.AssessmentSection.builder()
                                .title("What is Photosynthesis?")
                                .explanation("Photosynthesis is the process by which plants use sunlight, water, and carbon dioxide to produce glucose and oxygen.")
                                .example("For example, a leaf captures sunlight and uses it to make sugar for the plant.")
                                .build(),
                        AssessmentRequest.AssessmentSection.builder()
                                .title("Chlorophyll and Light Absorption")
                                .explanation("Chlorophyll is the green pigment in plants that absorbs light energy, primarily in the blue and red wavelengths.")
                                .example("The green color we see is because chlorophyll reflects green light.")
                                .build()
                ))
                .questionCount(5)
                .build();
    }

    private static final String ASSESSMENT_GENERATION_RESPONSE = """
            {
              "questions": [
                {
                  "type": "MCQ",
                  "question": "What is the primary pigment involved in photosynthesis?",
                  "options": ["Chlorophyll", "Hemoglobin", "Melanin", "Carotene"],
                  "correctOptionIndex": 0,
                  "concept": "Chlorophyll is the primary pigment for photosynthesis"
                },
                {
                  "type": "SHORT_ANSWER",
                  "question": "Explain how plants convert sunlight into chemical energy.",
                  "concept": "Light energy conversion in photosynthesis"
                },
                {
                  "type": "MCQ",
                  "question": "What are the products of photosynthesis?",
                  "options": ["Glucose and oxygen", "Carbon dioxide and water", "Glucose and carbon dioxide", "Oxygen and water"],
                  "correctOptionIndex": 1,
                  "concept": "Products of photosynthesis"
                },
                {
                  "type": "APPLICATION",
                  "question": "If a plant is placed in a dark room, what would happen to its ability to perform photosynthesis? Explain why.",
                  "concept": "Light dependency in photosynthesis"
                },
                {
                  "type": "SHORT_ANSWER",
                  "question": "Why do plants appear green to our eyes?",
                  "concept": "Light absorption and reflection by chlorophyll"
                }
              ]
            }
            """;

    private static final String EVALUATION_RESPONSE = """
            {
              "score": 4,
              "total": 5,
              "percentage": 80.0,
              "conceptsUnderstood": [
                "Chlorophyll is the primary pigment for photosynthesis",
                "Products of photosynthesis",
                "Light energy conversion in photosynthesis"
              ],
              "weakAreas": [
                "Light absorption and reflection by chlorophyll"
              ],
              "incorrectConcepts": [
                "Light absorption and reflection by chlorophyll"
              ],
              "revisionRecommendations": [
                {
                  "concept": "Light absorption and reflection by chlorophyll",
                  "recommendation": "Review how chlorophyll absorbs red and blue light but reflects green light, which is why plants appear green."
                }
              ],
              "suggestedNextTopic": "Cellular Respiration: How Plants Use the Glucose They Produce",
              "performanceSummary": "You demonstrated strong understanding of the core photosynthesis process. Focus on reviewing how light wavelengths interact with chlorophyll."
            }
            """;

    @Test
    void generatesAssessmentSuccessfully() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());

        assertNotNull(response);
        assertNotNull(response.getAssessmentId());
        assertEquals("Photosynthesis", response.getTopic());
        assertEquals("English", response.getLanguage());
        assertEquals(5, response.getQuestions().size());
        assertEquals(5, response.getTotalQuestions());
    }

    @Test
    void generatedQuestionsHaveCorrectTypes() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());

        assertEquals("MCQ", response.getQuestions().get(0).getType());
        assertEquals("SHORT_ANSWER", response.getQuestions().get(1).getType());
        assertEquals("MCQ", response.getQuestions().get(2).getType());
        assertEquals("APPLICATION", response.getQuestions().get(3).getType());
        assertEquals("SHORT_ANSWER", response.getQuestions().get(4).getType());
    }

    @Test
    void mcqQuestionsHaveOptions() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());

        AssessmentQuestion mcq = response.getQuestions().get(0);
        assertNotNull(mcq.getOptions());
        assertEquals(4, mcq.getOptions().size());
        assertTrue(mcq.getOptions().contains("Chlorophyll"));
    }

    @Test
    void mcqQuestionsCaptureCorrectOptionIndex() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());

        assertEquals(Integer.valueOf(0), response.getQuestions().get(0).getCorrectOptionIndex());
        assertEquals(Integer.valueOf(1), response.getQuestions().get(2).getCorrectOptionIndex());
        // Non-MCQ questions must not carry an index.
        assertEquals(null, response.getQuestions().get(1).getCorrectOptionIndex());
    }

    @Test
    void outOfRangeCorrectOptionIndexIsIgnored() {
        chatClient.respond("""
                {
                  "questions": [
                    {
                      "type": "MCQ",
                      "question": "Pick one?",
                      "options": ["A", "B", "C", "D"],
                      "correctOptionIndex": 9,
                      "concept": "Choosing"
                    }
                  ]
                }
                """);
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());
        assertEquals(null, response.getQuestions().get(0).getCorrectOptionIndex());
    }

    @Test
    void evaluationAlignsHintsPerQuestionAndNeverShiftsMcqResults() {
        // Question 0 is an MCQ without a captured index (semantic fallback), while
        // question 1 is an MCQ with an index. The old parallel-list implementation
        // crashed with IndexOutOfBoundsException here; the prompt must stay aligned.
        chatClient.respond(EVALUATION_RESPONSE);

        AssessmentSubmissionRequest request = AssessmentSubmissionRequest.builder()
                .assessmentId("test-assessment-123")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .lessonTitle("Introduction to Photosynthesis")
                .questions(List.of(
                        AssessmentQuestion.builder()
                                .type("MCQ")
                                .question("What is the primary pigment?")
                                .concept("Chlorophyll")
                                .options(List.of("Chlorophyll", "Hemoglobin", "Melanin", "Carotene"))
                                .build(),
                        AssessmentQuestion.builder()
                                .type("MCQ")
                                .question("What are the products?")
                                .concept("Products")
                                .correctOptionIndex(1)
                                .options(List.of("Glucose and oxygen", "Carbon dioxide and water", "Glucose and carbon dioxide", "Oxygen and water"))
                                .build(),
                        AssessmentQuestion.builder()
                                .type("SHORT_ANSWER")
                                .question("Explain photosynthesis.")
                                .concept("Photosynthesis process")
                                .build()
                ))
                .answers(List.of(
                        AssessmentSubmissionRequest.StudentAnswer.builder()
                                .question("What is the primary pigment?")
                                .answer("Hemoglobin")
                                .questionType("MCQ")
                                .selectedOptionIndex(1)
                                .build(),
                        AssessmentSubmissionRequest.StudentAnswer.builder()
                                .question("What are the products?")
                                .answer("Carbon dioxide and water")
                                .questionType("MCQ")
                                .selectedOptionIndex(1)
                                .build(),
                        AssessmentSubmissionRequest.StudentAnswer.builder()
                                .question("Explain photosynthesis.")
                                .answer("Plants use sunlight to make food.")
                                .questionType("SHORT_ANSWER")
                                .build()
                ))
                .build();

        AssessmentResult result = service("sk-test-123").evaluateAssessment(request);
        assertNotNull(result);
        assertEquals(4, result.getScore());

        ChatMessage userMessage = chatClient.lastUserMessage();
        // MCQ without a captured index is graded semantically (no crash, no shifted hints).
        assertTrue(userMessage.content().contains("MCQ Evaluation: SHORT_ANSWER/APPLICATION — evaluate semantically"));
        // MCQ with a captured index is graded by exact match.
        assertTrue(userMessage.content().contains("MCQ Evaluation: CORRECT (exact option match)"));
        assertTrue(userMessage.content().contains("Correct Option Index: 1"));
    }

    @Test
    void questionsHaveConcepts() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());

        for (AssessmentQuestion q : response.getQuestions()) {
            assertNotNull(q.getConcept(), "Question should have a concept: " + q.getQuestion());
        }
    }

    @Test
    void evaluatesAssessmentSuccessfully() {
        chatClient.respond(EVALUATION_RESPONSE);

        AssessmentSubmissionRequest request = AssessmentSubmissionRequest.builder()
                .assessmentId("test-assessment-123")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .lessonTitle("Introduction to Photosynthesis")
                .questions(List.of(
                        AssessmentQuestion.builder()
                                .type("MCQ")
                                .question("What is the primary pigment?")
                                .concept("Chlorophyll")
                                .correctOptionIndex(0)
                                .options(List.of("Chlorophyll", "Hemoglobin", "Melanin", "Carotene"))
                                .build(),
                        AssessmentQuestion.builder()
                                .type("SHORT_ANSWER")
                                .question("Explain photosynthesis.")
                                .concept("Photosynthesis process")
                                .build()
                ))
                .answers(List.of(
                        AssessmentSubmissionRequest.StudentAnswer.builder()
                                .question("What is the primary pigment?")
                                .answer("Chlorophyll")
                                .questionType("MCQ")
                                .selectedOptionIndex(0)
                                .build(),
                        AssessmentSubmissionRequest.StudentAnswer.builder()
                                .question("Explain photosynthesis.")
                                .answer("Plants use sunlight to make food.")
                                .questionType("SHORT_ANSWER")
                                .build()
                ))
                .build();

        AssessmentResult result = service("sk-test-123").evaluateAssessment(request);

        assertNotNull(result);
        assertEquals(4, result.getScore());
        assertEquals(5, result.getTotal());
        assertEquals(80.0, result.getPercentage(), 0.1);
        assertEquals(3, result.getConceptsUnderstood().size());
        assertEquals(1, result.getWeakAreas().size());
        assertEquals(1, result.getIncorrectConcepts().size());
        assertEquals(1, result.getRevisionRecommendations().size());
        assertNotNull(result.getSuggestedNextTopic());
        assertNotNull(result.getPerformanceSummary());
    }

    @Test
    void promptContainsLessonContent() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        service("sk-test-123").generateAssessment(sampleRequest());

        ChatMessage userMessage = chatClient.lastUserMessage();
        assertNotNull(userMessage);
        String prompt = userMessage.content();
        assertTrue(prompt.contains("Photosynthesis"));
        assertTrue(prompt.contains("High School"));
        assertTrue(prompt.contains("Example-Based"));
        assertTrue(prompt.contains("What is Photosynthesis?"));
        assertTrue(prompt.contains("Chlorophyll and Light Absorption"));
    }

    @Test
    void promptRequestsEnglishForEnglishLanguage() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        service("sk-test-123").generateAssessment(sampleRequest());

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in English"));
    }

    @Test
    void promptRequestsHindiForHindiLanguage() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentRequest hindiRequest = AssessmentRequest.builder()
                .topic("प्रकाश संश्लेषण")
                .language("Hindi")
                .educationLevel("School")
                .teachingStyle("Simple Explanation")
                .objective("प्रकाश संश्लेषण को समझना")
                .lessonTitle("प्रकाश संश्लेषण का परिचय")
                .introduction("प्रकाश संश्लेषण वह प्रक्रिया है जिससे पौधे सूर्य के प्रकाश को ऊर्जा में बदलते हैं।")
                .sections(List.of(
                        AssessmentRequest.AssessmentSection.builder()
                                .title("प्रकाश संश्लेषण क्या है?")
                                .explanation("प्रकाश संश्लेषण वह प्रक्रिया है जिसमें पौधे सूर्य के प्रकाश, पानी और कार्बन डाइऑक्साइड का उपयोग करके ग्लूकोज और ऑक्सीजन बनाते हैं।")
                                .build()
                ))
                .build();
        service("sk-test-123").generateAssessment(hindiRequest);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("in Hindi"));
    }

    @Test
    void missingApiKeyFailsAsUnavailable() {
        AiException ex = assertThrows(AiException.class,
                () -> service("   ").generateAssessment(sampleRequest()));
        assertTrue(ex.isUnavailable());
        assertEquals(0, chatClient.callCount);
    }

    @Test
    void missingApiKeyForEvaluationFailsAsUnavailable() {
        AiException ex = assertThrows(AiException.class,
                () -> service("   ").evaluateAssessment(
                        AssessmentSubmissionRequest.builder()
                                .assessmentId("test")
                                .topic("test")
                                .questions(List.of())
                                .answers(List.of())
                                .build()));
        assertTrue(ex.isUnavailable());
        assertEquals(0, chatClient.callCount);
    }

    @Test
    void upstreamProviderFailurePropagates() {
        chatClient.failWith(AiException.upstream("simulated HTTP 429"));
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateAssessment(sampleRequest()));
        assertEquals(AiException.Kind.UPSTREAM, ex.getKind());
    }

    @Test
    void nonJsonAiContentIsRejected() {
        chatClient.respond("Sorry, I cannot generate that assessment.");
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateAssessment(sampleRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
    }

    @Test
    void missingQuestionsArrayIsRejected() {
        chatClient.respond("{\"answer\": \"no questions here\"}");
        AiException ex = assertThrows(AiException.class,
                () -> service("sk-test-123").generateAssessment(sampleRequest()));
        assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
        assertTrue(ex.getMessage().contains("questions"));
    }

    @Test
    void markdownFencedJsonIsStillParsed() {
        chatClient.respond("```json\n" + ASSESSMENT_GENERATION_RESPONSE + "\n```");
        AssessmentResponse response = service("sk-test-123").generateAssessment(sampleRequest());
        assertEquals(5, response.getQuestions().size());
    }

    @Test
    void endpointAndModelArePassedToClient() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        service("sk-test-123").generateAssessment(sampleRequest());
        assertEquals("https://api.openai.com/v1", chatClient.lastBaseUrl);
        assertEquals("sk-test-123", chatClient.lastApiKey);
        assertEquals("gpt-4o-mini", chatClient.lastModel);
    }

    @Test
    void questionCountRespectedInPrompt() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentRequest request = sampleRequest();
        request.setQuestionCount(3);
        service("sk-test-123").generateAssessment(request);

        ChatMessage systemMessage = chatClient.lastSystemMessage();
        assertTrue(systemMessage.content().contains("exactly 3 questions"));
    }

    @Test
    void adaptiveSummaryIncludedInPrompt() {
        chatClient.respond(ASSESSMENT_GENERATION_RESPONSE);
        AssessmentRequest request = sampleRequest();
        request.setAdaptiveSummary(AssessmentRequest.AdaptiveTeachingSummary.builder()
                .totalAdaptations(2)
                .conceptsNeedingReteaching(List.of("Chlorophyll function"))
                .misconceptionsDetected(List.of("Confused chlorophyll with hemoglobin"))
                .build());
        service("sk-test-123").generateAssessment(request);

        ChatMessage userMessage = chatClient.lastUserMessage();
        assertTrue(userMessage.content().contains("ADAPTIVE TEACHING DATA"));
        assertTrue(userMessage.content().contains("Chlorophyll function"));
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
