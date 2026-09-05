package com.aiteacher.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aiteacher.dto.ProgressSaveRequest;
import com.aiteacher.dto.ProgressSummary;
import com.aiteacher.entity.LearningSession;
import com.aiteacher.service.ProgressService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link ProgressController}. Uses a mocked {@link ProgressService}
 * to verify request validation, endpoint wiring, and error handling.
 */
@WebMvcTest(ProgressController.class)
class ProgressControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProgressService progressService;

    @Autowired
    private ObjectMapper json;

    private ProgressSaveRequest validSaveRequest() {
        return ProgressSaveRequest.builder()
                .studentName("Test Student")
                .topic("Physics")
                .language("English")
                .educationalLevel("High School")
                .teachingStyle("Example-Based")
                .learningObjective("Understand Newton's Laws")
                .score(4)
                .totalQuestions(5)
                .percentage(80.0)
                .conceptsUnderstood(List.of("Force", "Mass"))
                .weakAreas(List.of("Newton's Third Law"))
                .incorrectConcepts(List.of("Momentum"))
                .revisionRecommendations(List.of("Review action-reaction pairs"))
                .suggestedNextTopic("Momentum and Impulse")
                .build();
    }

    private LearningSession sampleSavedSession() {
        return LearningSession.builder()
                .id(1L)
                .studentName("Test Student")
                .topic("Physics")
                .language("English")
                .educationalLevel("High School")
                .score(4)
                .totalQuestions(5)
                .percentage(80.0)
                .completedAt(LocalDateTime.now())
                .build();
    }

    private ProgressSummary sampleSummary() {
        return ProgressSummary.builder()
                .totalLessonsCompleted(3)
                .averageScore(80.0)
                .bestScore(100)
                .topicsStudied(List.of("Physics", "Biology", "Programming"))
                .weakConcepts(List.of("Newton's Third Law"))
                .strengths(List.of("Force", "Mass"))
                .recentActivity(List.of(
                        ProgressSummary.RecentActivity.builder()
                                .topic("Physics")
                                .percentage(80.0)
                                .completedAt("Jan 01, 2026 12:00")
                                .status("Strong")
                                .build()
                ))
                .latestRecommendations(List.of("Review action-reaction pairs"))
                .latestNextTopic("Momentum and Impulse")
                .build();
    }

    @Test
    void saveEndpointReturnsSavedSession() throws Exception {
        when(progressService.saveSession(any())).thenReturn(sampleSavedSession());

        mockMvc.perform(post("/api/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validSaveRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Test Student"))
                .andExpect(jsonPath("$.topic").value("Physics"))
                .andExpect(jsonPath("$.percentage").value(80.0));
    }

    @Test
    void saveEndpointRejectsMissingStudentName() throws Exception {
        ProgressSaveRequest request = validSaveRequest();
        request.setStudentName(null);

        mockMvc.perform(post("/api/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("studentName must not be empty"));
    }

    @Test
    void saveEndpointRejectsMissingTopic() throws Exception {
        ProgressSaveRequest request = validSaveRequest();
        request.setTopic(null);

        mockMvc.perform(post("/api/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("topic must not be empty"));
    }

    @Test
    void historyEndpointReturnsSessions() throws Exception {
        List<LearningSession> sessions = List.of(sampleSavedSession());
        when(progressService.getStudentHistory("Test Student")).thenReturn(sessions);

        mockMvc.perform(get("/api/progress")
                        .param("studentName", "Test Student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("Physics"));
    }

    @Test
    void historyEndpointRejectsMissingStudentName() throws Exception {
        mockMvc.perform(get("/api/progress")
                        .param("studentName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("studentName must not be empty"));
    }

    @Test
    void summaryEndpointReturnsSummary() throws Exception {
        when(progressService.getProgressSummary("Test Student")).thenReturn(sampleSummary());

        mockMvc.perform(get("/api/progress/summary")
                        .param("studentName", "Test Student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLessonsCompleted").value(3))
                .andExpect(jsonPath("$.averageScore").value(80.0))
                .andExpect(jsonPath("$.bestScore").value(100))
                .andExpect(jsonPath("$.topicsStudied.length()").value(3));
    }

    @Test
    void summaryEndpointRejectsMissingStudentName() throws Exception {
        mockMvc.perform(get("/api/progress/summary")
                        .param("studentName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("studentName must not be empty"));
    }

    @Test
    void historyEndpointReturnsEmptyListForNewStudent() throws Exception {
        when(progressService.getStudentHistory("New Student")).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/progress")
                        .param("studentName", "New Student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void summaryEndpointReturnsZeroForNewStudent() throws Exception {
        when(progressService.getProgressSummary("New Student")).thenReturn(
                ProgressSummary.builder()
                        .totalLessonsCompleted(0)
                        .averageScore(0.0)
                        .bestScore(0)
                        .topicsStudied(new ArrayList<>())
                        .weakConcepts(new ArrayList<>())
                        .strengths(new ArrayList<>())
                        .recentActivity(new ArrayList<>())
                        .latestRecommendations(new ArrayList<>())
                        .latestNextTopic(null)
                        .build()
        );

        mockMvc.perform(get("/api/progress/summary")
                        .param("studentName", "New Student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLessonsCompleted").value(0))
                .andExpect(jsonPath("$.averageScore").value(0.0))
                .andExpect(jsonPath("$.bestScore").value(0));
    }
}
