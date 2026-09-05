package com.aiteacher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aiteacher.dto.ProgressSaveRequest;
import com.aiteacher.dto.ProgressSummary;
import com.aiteacher.entity.LearningSession;
import com.aiteacher.repository.LearningSessionRepository;

/**
 * Unit tests for {@link ProgressServiceImpl}.
 * Tests progress calculation and data retrieval from stored sessions.
 */
@ExtendWith(MockitoExtension.class)
class ProgressServiceImplTests {

    @Mock
    private LearningSessionRepository sessionRepository;

    @InjectMocks
    private ProgressServiceImpl progressService;

    private LearningSession sampleSession1;
    private LearningSession sampleSession2;
    private LearningSession sampleSession3;

    @BeforeEach
    void setUp() {
        sampleSession1 = LearningSession.builder()
                .id(1L)
                .studentName("Test Student")
                .topic("Physics")
                .language("English")
                .educationalLevel("High School")
                .teachingStyle("Example-Based")
                .learningObjective("Understand Newton's Laws")
                .score(4)
                .totalQuestions(5)
                .percentage(80.0)
                .conceptsUnderstood(List.of("Force", "Mass", "Acceleration"))
                .weakAreas(List.of("Newton's Third Law"))
                .incorrectConcepts(List.of("Momentum"))
                .revisionRecommendations(List.of("Newton's Third Law: Review action-reaction pairs"))
                .suggestedNextTopic("Momentum and Impulse")
                .completedAt(LocalDateTime.now().minusDays(2))
                .build();

        sampleSession2 = LearningSession.builder()
                .id(2L)
                .studentName("Test Student")
                .topic("Biology")
                .language("English")
                .educationalLevel("High School")
                .teachingStyle("Visual")
                .learningObjective("Understand Cell Structure")
                .score(3)
                .totalQuestions(5)
                .percentage(60.0)
                .conceptsUnderstood(List.of("Cell Membrane", "Nucleus"))
                .weakAreas(List.of("Mitochondria", "Ribosomes"))
                .incorrectConcepts(List.of("Golgi Apparatus"))
                .revisionRecommendations(List.of("Mitochondria: Review ATP production"))
                .suggestedNextTopic("Cell Division")
                .completedAt(LocalDateTime.now().minusDays(1))
                .build();

        sampleSession3 = LearningSession.builder()
                .id(3L)
                .studentName("Test Student")
                .topic("Programming")
                .language("English")
                .educationalLevel("College")
                .teachingStyle("Step-by-Step")
                .learningObjective("Learn Python Basics")
                .score(5)
                .totalQuestions(5)
                .percentage(100.0)
                .conceptsUnderstood(List.of("Variables", "Functions", "Loops"))
                .weakAreas(new ArrayList<>())
                .incorrectConcepts(new ArrayList<>())
                .revisionRecommendations(new ArrayList<>())
                .suggestedNextTopic("Object-Oriented Programming")
                .completedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void savesSessionSuccessfully() {
        when(sessionRepository.save(any())).thenReturn(sampleSession1);

        ProgressSaveRequest request = ProgressSaveRequest.builder()
                .studentName("Test Student")
                .topic("Physics")
                .language("English")
                .educationalLevel("High School")
                .teachingStyle("Example-Based")
                .learningObjective("Understand Newton's Laws")
                .score(4)
                .totalQuestions(5)
                .percentage(80.0)
                .conceptsUnderstood(List.of("Force", "Mass", "Acceleration"))
                .weakAreas(List.of("Newton's Third Law"))
                .incorrectConcepts(List.of("Momentum"))
                .revisionRecommendations(List.of("Newton's Third Law: Review action-reaction pairs"))
                .suggestedNextTopic("Momentum and Impulse")
                .build();

        LearningSession saved = progressService.saveSession(request);

        assertNotNull(saved);
        assertEquals("Test Student", saved.getStudentName());
        assertEquals("Physics", saved.getTopic());
        assertEquals(80.0, saved.getPercentage(), 0.1);
    }

    @Test
    void getsStudentHistory() {
        List<LearningSession> sessions = List.of(sampleSession3, sampleSession2, sampleSession1);
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        List<LearningSession> history = progressService.getStudentHistory("Test Student");

        assertNotNull(history);
        assertEquals(3, history.size());
        assertEquals("Programming", history.get(0).getTopic());
    }

    @Test
    void getsEmptyHistoryForNewStudent() {
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("New Student")).thenReturn(new ArrayList<>());

        List<LearningSession> history = progressService.getStudentHistory("New Student");

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void calculatesProgressSummaryCorrectly() {
        List<LearningSession> sessions = List.of(sampleSession3, sampleSession2, sampleSession1);
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        ProgressSummary summary = progressService.getProgressSummary("Test Student");

        assertNotNull(summary);
        assertEquals(3, summary.getTotalLessonsCompleted());
        assertEquals(80.0, summary.getAverageScore(), 0.1); // (80 + 60 + 100) / 3 = 80
        assertEquals(100, summary.getBestScore());
        assertEquals(3, summary.getTopicsStudied().size());
        assertTrue(summary.getTopicsStudied().contains("Physics"));
        assertTrue(summary.getTopicsStudied().contains("Biology"));
        assertTrue(summary.getTopicsStudied().contains("Programming"));
        assertNotNull(summary.getRecentActivity());
        assertEquals(3, summary.getRecentActivity().size());
    }

    @Test
    void calculatesWeakConceptsFromMultipleSessions() {
        // Create sessions with shared weak areas using mutable lists
        LearningSession session1 = LearningSession.builder()
                .id(1L)
                .studentName("Test Student")
                .topic("Physics")
                .percentage(80.0)
                .weakAreas(new ArrayList<>(List.of("Newton's Third Law")))
                .conceptsUnderstood(new ArrayList<>(List.of("Force", "Mass")))
                .completedAt(LocalDateTime.now().minusDays(1))
                .build();
        LearningSession session2 = LearningSession.builder()
                .id(2L)
                .studentName("Test Student")
                .topic("Biology")
                .percentage(60.0)
                .weakAreas(new ArrayList<>(List.of("Newton's Third Law", "Mitochondria")))
                .conceptsUnderstood(new ArrayList<>(List.of("Force", "Cell Membrane")))
                .completedAt(LocalDateTime.now())
                .build();

        List<LearningSession> sessions = List.of(session2, session1);
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        ProgressSummary summary = progressService.getProgressSummary("Test Student");

        assertNotNull(summary);
        assertTrue(summary.getWeakConcepts().contains("Newton's Third Law"));
    }

    @Test
    void calculatesStrengthsFromMultipleSessions() {
        // Create sessions with shared understood concepts using mutable lists
        LearningSession session1 = LearningSession.builder()
                .id(1L)
                .studentName("Test Student")
                .topic("Physics")
                .percentage(80.0)
                .weakAreas(new ArrayList<>(List.of("Newton's Third Law")))
                .conceptsUnderstood(new ArrayList<>(List.of("Force", "Mass")))
                .completedAt(LocalDateTime.now().minusDays(1))
                .build();
        LearningSession session2 = LearningSession.builder()
                .id(2L)
                .studentName("Test Student")
                .topic("Biology")
                .percentage(60.0)
                .weakAreas(new ArrayList<>(List.of("Mitochondria")))
                .conceptsUnderstood(new ArrayList<>(List.of("Force", "Cell Membrane")))
                .completedAt(LocalDateTime.now())
                .build();

        List<LearningSession> sessions = List.of(session2, session1);
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        ProgressSummary summary = progressService.getProgressSummary("Test Student");

        assertNotNull(summary);
        assertTrue(summary.getStrengths().contains("Force"));
    }

    @Test
    void returnsEmptySummaryForNewStudent() {
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("New Student")).thenReturn(new ArrayList<>());

        ProgressSummary summary = progressService.getProgressSummary("New Student");

        assertNotNull(summary);
        assertEquals(0, summary.getTotalLessonsCompleted());
        assertEquals(0.0, summary.getAverageScore(), 0.1);
        assertEquals(0, summary.getBestScore());
        assertTrue(summary.getTopicsStudied().isEmpty());
        assertTrue(summary.getWeakConcepts().isEmpty());
        assertTrue(summary.getStrengths().isEmpty());
        assertTrue(summary.getRecentActivity().isEmpty());
    }

    @Test
    void getsLatestRecommendations() {
        List<LearningSession> sessions = List.of(sampleSession3, sampleSession2, sampleSession1);
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        ProgressSummary summary = progressService.getProgressSummary("Test Student");

        assertNotNull(summary);
        // session3 has no recommendations, so should get from session2
        assertFalse(summary.getLatestRecommendations().isEmpty());
    }

    @Test
    void getsLatestNextTopic() {
        List<LearningSession> sessions = List.of(sampleSession3, sampleSession2, sampleSession1);
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        ProgressSummary summary = progressService.getProgressSummary("Test Student");

        assertNotNull(summary);
        assertEquals("Object-Oriented Programming", summary.getLatestNextTopic());
    }

    @Test
    void recentActivityLimitedToFive() {
        // Create 6 sessions
        List<LearningSession> sessions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sessions.add(LearningSession.builder()
                    .id((long) i)
                    .studentName("Test Student")
                    .topic("Topic " + i)
                    .percentage(70.0 + i)
                    .completedAt(LocalDateTime.now().minusHours(i))
                    .build());
        }
        when(sessionRepository.findByStudentNameOrderByCompletedAtDesc("Test Student")).thenReturn(sessions);

        ProgressSummary summary = progressService.getProgressSummary("Test Student");

        assertNotNull(summary);
        assertEquals(5, summary.getRecentActivity().size());
    }

    private void assertFalse(boolean empty) {
        org.junit.jupiter.api.Assertions.assertFalse(empty);
    }
}
