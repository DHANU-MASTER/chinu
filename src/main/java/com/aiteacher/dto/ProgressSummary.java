package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 11 response containing the student's progress summary.
 * All values are dynamically calculated from stored learning sessions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressSummary {

    /** Total number of lessons completed. */
    private int totalLessonsCompleted;

    /** Average score across all sessions. */
    private double averageScore;

    /** Best score achieved. */
    private int bestScore;

    /** List of all topics studied. */
    private List<String> topicsStudied;

    /** Common weak areas across sessions. */
    private List<String> weakConcepts;

    /** Concepts the student consistently understands. */
    private List<String> strengths;

    /** Recent learning activity. */
    private List<RecentActivity> recentActivity;

    /** Most recent revision recommendations. */
    private List<String> latestRecommendations;

    /** Most recent suggested next topic. */
    private String latestNextTopic;

    /**
     * Represents a recent learning activity.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        /** The topic studied. */
        private String topic;

        /** The score achieved. */
        private int score;

        /** Score as percentage. */
        private double percentage;

        /** When the session was completed. */
        private String completedAt;

        /** Status based on score. */
        private String status;
    }
}
