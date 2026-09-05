package com.aiteacher.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aiteacher.ai.AiException;
import com.aiteacher.dto.AdaptiveTeachingResponse;
import com.aiteacher.dto.EvaluationResponse;
import com.aiteacher.dto.MisconceptionResponse;
import com.aiteacher.dto.QuestionResponse;
import com.aiteacher.service.AdaptiveTeachingService;
import com.aiteacher.service.AnswerEvaluationService;
import com.aiteacher.service.MisconceptionDetectionService;
import com.aiteacher.service.QuestionGenerationService;

/**
 * Web-slice tests for {@link LessonInteractionController}: validation,
 * success mapping, and error handling for all endpoints. The services are
 * mocked, so these tests are deterministic and never touch the network.
 */
@WebMvcTest(LessonInteractionController.class)
class LessonInteractionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionGenerationService questionService;

    @MockitoBean
    private AnswerEvaluationService evaluationService;

    @MockitoBean
    private MisconceptionDetectionService misconceptionService;

    @MockitoBean
    private AdaptiveTeachingService adaptiveService;

    // ---- Question generation tests ----

    private static final String VALID_QUESTION_BODY = """
            {
              "topic": "Photosynthesis",
              "language": "English",
              "educationLevel": "High School",
              "teachingStyle": "Simple Explanation",
              "objective": "Understand the basics",
              "sectionTitle": "What is Photosynthesis",
              "sectionContent": "Photosynthesis is the process by which plants convert sunlight into energy.",
              "sectionExample": "Leaves use chlorophyll to capture light energy."
            }
            """;

    @Test
    void returnsGeneratedQuestionForValidInput() throws Exception {
        QuestionResponse question = QuestionResponse.builder()
                .type("MCQ")
                .question("What is the primary pigment?")
                .options(List.of("Chlorophyll", "Hemoglobin", "Melanin", "Carotene"))
                .correctOptionIndex(0)
                .expectedConcept("Chlorophyll is the main pigment")
                .questionId("test-id-123")
                .build();
        when(questionService.generateQuestion(any())).thenReturn(question);

        mockMvc.perform(post("/api/lesson/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUESTION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MCQ"))
                .andExpect(jsonPath("$.question").value("What is the primary pigment?"))
                .andExpect(jsonPath("$.options.length()").value(4))
                .andExpect(jsonPath("$.correctOptionIndex").value(0));

        verify(questionService).generateQuestion(any());
    }

    @Test
    void rejectsBlankTopicWith400() throws Exception {
        mockMvc.perform(post("/api/lesson/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "topic": "   ", "language": "English", "educationLevel": "High School",
                                  "sectionTitle": "Section", "sectionContent": "Content" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("topic must not be empty"));
        verify(questionService, never()).generateQuestion(any());
    }

    @Test
    void unconfiguredAiReturns503ForQuestion() throws Exception {
        when(questionService.generateQuestion(any()))
                .thenThrow(AiException.unavailable("AI_API_KEY is not set"));

        mockMvc.perform(post("/api/lesson/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUESTION_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void upstreamAiFailureReturns502ForQuestion() throws Exception {
        when(questionService.generateQuestion(any()))
                .thenThrow(AiException.upstream("HTTP 429"));

        mockMvc.perform(post("/api/lesson/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUESTION_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Unable to generate a question. Please retry."));
    }

    // ---- Answer evaluation tests ----

    private static final String VALID_EVALUATE_BODY = """
            {
              "question": "What is photosynthesis?",
              "questionType": "MCQ",
              "studentAnswer": "Chlorophyll",
              "correctOptionIndex": 0,
              "lessonSection": "Photosynthesis uses chlorophyll.",
              "topic": "Photosynthesis",
              "language": "English",
              "educationLevel": "High School"
            }
            """;

    @Test
    void returnsEvaluationForValidInput() throws Exception {
        EvaluationResponse evaluation = EvaluationResponse.builder()
                .status("CORRECT")
                .feedback("Correct!")
                .expectedConcept("Chlorophyll")
                .confidence(0.95)
                .build();
        when(evaluationService.evaluateAnswer(any())).thenReturn(evaluation);

        mockMvc.perform(post("/api/lesson/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVALUATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CORRECT"));
        verify(evaluationService).evaluateAnswer(any());
    }

    @Test
    void unconfiguredAiReturns503ForEvaluation() throws Exception {
        when(evaluationService.evaluateAnswer(any()))
                .thenThrow(AiException.unavailable("AI_API_KEY is not set"));

        mockMvc.perform(post("/api/lesson/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVALUATE_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    // ---- Misconception detection tests ----

    private static final String VALID_MISCONCEPTION_BODY = """
            {
              "question": "What is the primary pigment?",
              "questionType": "MCQ",
              "studentAnswer": "Hemoglobin",
              "correctConcept": "Chlorophyll",
              "evaluationStatus": "INCORRECT",
              "evaluationFeedback": "Not quite.",
              "lessonSection": "Photosynthesis uses chlorophyll.",
              "sectionTitle": "What is Photosynthesis",
              "topic": "Photosynthesis",
              "language": "English",
              "educationLevel": "High School",
              "teachingStyle": "Example-Based",
              "objective": "Understand the basics"
            }
            """;

    @Test
    void returnsMisconceptionForValidInput() throws Exception {
        MisconceptionResponse response = MisconceptionResponse.builder()
                .understanding("MISCONCEPTION")
                .misconception("Student confused chlorophyll with hemoglobin")
                .severity("MEDIUM")
                .explanationNeeded(true)
                .recommendedApproach("ANALOGY")
                .build();
        when(misconceptionService.detectMisconception(any())).thenReturn(response);

        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MISCONCEPTION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understanding").value("MISCONCEPTION"))
                .andExpect(jsonPath("$.explanationNeeded").value(true))
                .andExpect(jsonPath("$.recommendedApproach").value("ANALOGY"));

        verify(misconceptionService).detectMisconception(any());
    }

    @Test
    void returnsUnderstandingWhenCorrect() throws Exception {
        MisconceptionResponse response = MisconceptionResponse.builder()
                .understanding("UNDERSTOOD")
                .explanationNeeded(false)
                .recommendedApproach("EXAMPLE_BASED")
                .build();
        when(misconceptionService.detectMisconception(any())).thenReturn(response);

        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MISCONCEPTION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understanding").value("UNDERSTOOD"))
                .andExpect(jsonPath("$.explanationNeeded").value(false));
    }

    @Test
    void rejectsBlankQuestionForMisconception() throws Exception {
        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "", "studentAnswer": "test", "correctConcept": "concept",
                                  "lessonSection": "section", "topic": "topic", "language": "English",
                                  "educationLevel": "High School" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question must not be empty"));
        verify(misconceptionService, never()).detectMisconception(any());
    }

    @Test
    void rejectsBlankCorrectConceptForMisconception() throws Exception {
        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "question": "test?", "studentAnswer": "answer", "correctConcept": "",
                                  "lessonSection": "section", "topic": "topic", "language": "English",
                                  "educationLevel": "High School" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("correctConcept must not be empty"));
    }

    @Test
    void unconfiguredAiReturns503ForMisconception() throws Exception {
        when(misconceptionService.detectMisconception(any()))
                .thenThrow(AiException.unavailable("AI_API_KEY is not set"));

        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MISCONCEPTION_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void upstreamAiFailureReturns502ForMisconception() throws Exception {
        when(misconceptionService.detectMisconception(any()))
                .thenThrow(AiException.upstream("HTTP 429"));

        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MISCONCEPTION_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Unable to analyze your answer right now. Please try again."));
    }

    // ---- Adaptive teaching tests ----

    private static final String VALID_ADAPT_BODY = """
            {
              "understanding": "MISCONCEPTION",
              "misconception": "Student confused concepts",
              "recommendedApproach": "ANALOGY",
              "originalExplanation": "Original explanation text.",
              "sectionTitle": "Section",
              "topic": "Photosynthesis",
              "language": "English",
              "educationLevel": "High School",
              "teachingStyle": "Example-Based",
              "objective": "Learn basics",
              "studentAnswer": "wrong answer",
              "question": "What is X?",
              "adaptationCount": 0
            }
            """;

    @Test
    void returnsAdaptationForValidInput() throws Exception {
        AdaptiveTeachingResponse response = AdaptiveTeachingResponse.builder()
                .adaptationType("ANALOGY")
                .misconception("Student confused concepts")
                .reExplanation("Think of it like a solar panel...")
                .example("Imagine your phone charger...")
                .followUpQuestion("If a plant had no chlorophyll, what would happen?")
                .followUpQuestionType("MCQ")
                .followUpOptions(List.of("A", "B", "C", "D"))
                .followUpCorrectOptionIndex(1)
                .build();
        when(adaptiveService.generateAdaptation(any())).thenReturn(response);

        mockMvc.perform(post("/api/lesson/adapt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ADAPT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptationType").value("ANALOGY"))
                .andExpect(jsonPath("$.reExplanation").value("Think of it like a solar panel..."))
                .andExpect(jsonPath("$.followUpQuestionType").value("MCQ"))
                .andExpect(jsonPath("$.followUpOptions.length()").value(4));

        verify(adaptiveService).generateAdaptation(any());
    }

    @Test
    void rejectsBlankUnderstandingForAdapt() throws Exception {
        mockMvc.perform(post("/api/lesson/adapt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "understanding": "", "originalExplanation": "text", "topic": "topic",
                                  "language": "English", "educationLevel": "High School",
                                  "question": "Q?", "studentAnswer": "A" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("understanding must not be empty"));
        verify(adaptiveService, never()).generateAdaptation(any());
    }

    @Test
    void rejectsBlankOriginalExplanationForAdapt() throws Exception {
        mockMvc.perform(post("/api/lesson/adapt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "understanding": "MISCONCEPTION", "originalExplanation": "", "topic": "topic",
                                  "language": "English", "educationLevel": "High School",
                                  "question": "Q?", "studentAnswer": "A" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("originalExplanation must not be empty"));
    }

    @Test
    void unconfiguredAiReturns503ForAdapt() throws Exception {
        when(adaptiveService.generateAdaptation(any()))
                .thenThrow(AiException.unavailable("AI_API_KEY is not set"));

        mockMvc.perform(post("/api/lesson/adapt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ADAPT_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void upstreamAiFailureReturns502ForAdapt() throws Exception {
        when(adaptiveService.generateAdaptation(any()))
                .thenThrow(AiException.upstream("HTTP 429"));

        mockMvc.perform(post("/api/lesson/adapt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ADAPT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Unable to generate adaptive explanation. Please try again."));
    }

    @Test
    void returnsPartialUnderstandingFromMisconception() throws Exception {
        MisconceptionResponse response = MisconceptionResponse.builder()
                .understanding("PARTIAL")
                .misconception("Student knows some but not all")
                .severity("LOW")
                .explanationNeeded(true)
                .recommendedApproach("SIMPLER")
                .build();
        when(misconceptionService.detectMisconception(any())).thenReturn(response);

        mockMvc.perform(post("/api/lesson/misconception")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MISCONCEPTION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understanding").value("PARTIAL"))
                .andExpect(jsonPath("$.recommendedApproach").value("SIMPLER"));
    }
}
