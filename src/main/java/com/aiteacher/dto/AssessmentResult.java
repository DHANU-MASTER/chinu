package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 8 assessment result containing the student's score,
 * performance analysis, and recommendations.
 * All values come from actual evaluation of student answers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResult {

    /** Number of correct answers. */
    private int score;

    /** Total number of questions. */
    private int total;

    /** Score as percentage (0-100). */
    private double percentage;

    /** Concepts the student demonstrated understanding of. */
    private List<String> conceptsUnderstood;

    /** Areas where the student needs more practice. */
    private List<String> weakAreas;

    /** Concepts the student answered incorrectly. */
    private List<String> incorrectConcepts;

    /** Personalized revision recommendations. */
    private List<RevisionRecommendation> revisionRecommendations;

    /** AI-generated suggested next topic based on performance. */
    private String suggestedNextTopic;

    /** Brief summary of the student's performance. */
    private String performanceSummary;

    /**
     * A specific revision recommendation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevisionRecommendation {
        /** The weak concept that needs revision. */
        private String concept;

        /** Specific recommendation for improvement. */
        private String recommendation;
    }
}
