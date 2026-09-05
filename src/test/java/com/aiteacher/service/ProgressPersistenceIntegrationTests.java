package com.aiteacher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.aiteacher.dto.ProgressSaveRequest;

/**
 * Integration test against the real H2 database: saves an actual learning
 * session and reads it back through the progress service, including the
 * element-collection fields. Guards against lazy-loading regressions that
 * only appear with real data (the dashboard 500 bug).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aiteachertest;DB_CLOSE_DELAY=-1")
class ProgressPersistenceIntegrationTests {

    @Autowired
    private ProgressService progressService;

    @Test
    void savesAndReadsBackRealSessionWithCollections() {
        ProgressSaveRequest request = ProgressSaveRequest.builder()
                .studentName("Integration Student")
                .topic("Quantum Computing")
                .language("English")
                .educationalLevel("College")
                .teachingStyle("Visual")
                .learningObjective("Understand qubits")
                .score(4)
                .totalQuestions(5)
                .percentage(80.0)
                .conceptsUnderstood(List.of("Superposition", "Qubits"))
                .weakAreas(List.of("Entanglement"))
                .incorrectConcepts(List.of("Entanglement"))
                .revisionRecommendations(List.of("Entanglement: review Bell pairs"))
                .suggestedNextTopic("Quantum Entanglement")
                .build();

        var saved = progressService.saveSession(request);
        assertNotNull(saved.getId());

        // History must include the real session with its collections readable.
        var history = progressService.getStudentHistory("Integration Student");
        assertFalse(history.isEmpty());
        assertEquals("Quantum Computing", history.get(0).getTopic());
        assertTrue(history.get(0).getConceptsUnderstood().contains("Superposition"));
        assertTrue(history.get(0).getWeakAreas().contains("Entanglement"));

        // Summary must be computed from the real stored data (no lazy-loading crash).
        var summary = progressService.getProgressSummary("Integration Student");
        assertEquals(1, summary.getTotalLessonsCompleted());
        assertEquals(80.0, summary.getAverageScore(), 0.1);
        assertEquals(80, summary.getBestScore());
        assertEquals(List.of("Quantum Computing"), summary.getTopicsStudied());
        assertTrue(summary.getWeakConcepts().contains("Entanglement"));
        assertTrue(summary.getStrengths().contains("Superposition"));
        assertEquals("Quantum Entanglement", summary.getLatestNextTopic());
        assertFalse(summary.getRecentActivity().isEmpty());
    }

    @Test
    void emptyStudentHasCleanEmptySummary() {
        var summary = progressService.getProgressSummary("Nobody");
        assertEquals(0, summary.getTotalLessonsCompleted());
        assertEquals(0.0, summary.getAverageScore(), 0.0);
        assertTrue(summary.getTopicsStudied().isEmpty());
        assertTrue(summary.getRecentActivity().isEmpty());
    }
}