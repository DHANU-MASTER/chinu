package com.aiteacher.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.aiteacher.ai.AiException;
import com.aiteacher.dto.AssessmentQuestion;
import com.aiteacher.dto.AssessmentRequest;
import com.aiteacher.dto.AssessmentResponse;
import com.aiteacher.dto.AssessmentResult;
import com.aiteacher.dto.AssessmentSubmissionRequest;
import com.aiteacher.service.AssessmentService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link AssessmentController}. Uses a mocked {@link AssessmentService}
 * to verify request validation, endpoint wiring, and error handling.
 */
@WebMvcTest(AssessmentController.class)
class AssessmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentService assessmentService;

    @Autowired
    private ObjectMapper json;

    private AssessmentRequest validGenerateRequest() {
        return AssessmentRequest.builder()
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .teachingStyle("Example-Based")
                .objective("Understand photosynthesis")
                .lessonTitle("Introduction to Photosynthesis")
                .introduction("Photosynthesis converts sunlight to energy.")
                .sections(List.of(
                        AssessmentRequest.AssessmentSection.builder()
                                .title("Section 1")
                                .explanation("Content here.")
                                .build()
                ))
                .questionCount(5)
                .build();
    }

    private AssessmentSubmissionRequest validSubmitRequest() {
        return AssessmentSubmissionRequest.builder()
                .assessmentId("test-123")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .lessonTitle("Introduction to Photosynthesis")
                .questions(List.of(
                        AssessmentQuestion.builder()
                                .type("MCQ")
                                .question("What is photosynthesis?")
                                .concept("Photosynthesis")
                                .build()
                ))
                .answers(List.of(
                        AssessmentSubmissionRequest.StudentAnswer.builder()
                                .question("What is photosynthesis?")
                                .answer("Chlorophyll")
                                .questionType("MCQ")
                                .selectedOptionIndex(0)
                                .build()
                ))
                .build();
    }

    private AssessmentResponse sampleAssessmentResponse() {
        return AssessmentResponse.builder()
                .assessmentId("test-123")
                .topic("Photosynthesis")
                .language("English")
                .educationLevel("High School")
                .questions(List.of(
                        AssessmentQuestion.builder()
                                .type("MCQ")
                                .question("What is photosynthesis?")
                                .options(List.of("A", "B", "C", "D"))
                                .concept("Photosynthesis")
                                .build()
                ))
                .totalQuestions(1)
                .build();
    }

    private AssessmentResult sampleResult() {
        return AssessmentResult.builder()
                .score(4)
                .total(5)
                .percentage(80.0)
                .conceptsUnderstood(List.of("Concept A"))
                .weakAreas(List.of("Concept B"))
                .incorrectConcepts(List.of("Concept C"))
                .revisionRecommendations(List.of(
                        AssessmentResult.RevisionRecommendation.builder()
                                .concept("Concept B")
                                .recommendation("Review this concept.")
                                .build()
                ))
                .suggestedNextTopic("Advanced Photosynthesis")
                .performanceSummary("Good performance overall.")
                .build();
    }

    @Test
    void generateEndpointReturnsAssessment() throws Exception {
        when(assessmentService.generateAssessment(any())).thenReturn(sampleAssessmentResponse());

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validGenerateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value("test-123"))
                .andExpect(jsonPath("$.topic").value("Photosynthesis"))
                .andExpect(jsonPath("$.totalQuestions").value(1));
    }

    @Test
    void generateEndpointRejectsMissingTopic() throws Exception {
        AssessmentRequest request = validGenerateRequest();
        request.setTopic(null);
        request.setLessonTitle(null);

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void generateEndpointRejectsMissingLanguage() throws Exception {
        AssessmentRequest request = validGenerateRequest();
        request.setLanguage(null);

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("language must not be empty"));
    }

    @Test
    void generateEndpointRejectsMissingEducationLevel() throws Exception {
        AssessmentRequest request = validGenerateRequest();
        request.setEducationLevel(null);

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("educationLevel must not be empty"));
    }

    @Test
    void generateEndpointRejectsEmptySections() throws Exception {
        AssessmentRequest request = validGenerateRequest();
        request.setSections(List.of());

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sections must not be empty"));
    }

    @Test
    void submitEndpointReturnsResult() throws Exception {
        when(assessmentService.evaluateAssessment(any())).thenReturn(sampleResult());

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.percentage").value(80.0));
    }

    @Test
    void submitEndpointRejectsMissingAssessmentId() throws Exception {
        AssessmentSubmissionRequest request = validSubmitRequest();
        request.setAssessmentId(null);

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("assessmentId must not be empty"));
    }

    @Test
    void submitEndpointRejectsEmptyQuestions() throws Exception {
        AssessmentSubmissionRequest request = validSubmitRequest();
        request.setQuestions(List.of());

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("questions must not be empty"));
    }

    @Test
    void submitEndpointRejectsEmptyAnswers() throws Exception {
        AssessmentSubmissionRequest request = validSubmitRequest();
        request.setAnswers(List.of());

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("answers must not be empty"));
    }

    @Test
    void submitEndpointRejectsMismatchedCounts() throws Exception {
        AssessmentSubmissionRequest request = validSubmitRequest();
        request.setAnswers(List.of()); // empty answers but questions exist

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void generateEndpointHandlesAiUnavailable() throws Exception {
        when(assessmentService.generateAssessment(any()))
                .thenThrow(AiException.unavailable("API key not set"));

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validGenerateRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void submitEndpointHandlesAiUnavailable() throws Exception {
        when(assessmentService.evaluateAssessment(any()))
                .thenThrow(AiException.unavailable("API key not set"));

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void generateEndpointHandlesUpstreamFailure() throws Exception {
        when(assessmentService.generateAssessment(any()))
                .thenThrow(AiException.upstream("HTTP 500"));

        mockMvc.perform(post("/api/assessment/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validGenerateRequest())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void submitEndpointHandlesUpstreamFailure() throws Exception {
        when(assessmentService.evaluateAssessment(any()))
                .thenThrow(AiException.upstream("HTTP 500"));

        mockMvc.perform(post("/api/assessment/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }
}
