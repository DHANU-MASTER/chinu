package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 8 request for assessment generation.
 * Carries the actual lesson content and student profile to generate
 * dynamic assessment questions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentRequest {

    /** The actual topic being assessed. */
    private String topic;

    /** Student's preferred language. */
    private String language;

    /** Student's educational level. */
    private String educationLevel;

    /** Student's teaching style preference. */
    private String teachingStyle;	/** Student's learning objective. */
	private String objective;

	/** Optional prior knowledge, used to tune question difficulty. */
	private String priorKnowledge;

	/** Optional available study time, used to keep the assessment focused. */
	private String availableTime;

	/** Optional desired depth (Quick/Standard/Deep). */
	private String desiredDepth;

	/** The lesson title. */
    private String lessonTitle;

    /** The lesson introduction. */
    private String introduction;

    /** List of section titles and content for context. */
    private List<AssessmentSection> sections;

    /** Number of questions to generate (default 5). */
    @Builder.Default
    private int questionCount = 5;

    /** Any adaptive teaching data from Phase 7 (optional). */
    private AdaptiveTeachingSummary adaptiveSummary;

    /**
     * Represents a lesson section for assessment context.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssessmentSection {
        private String title;
        private String explanation;
        private String example;
    }

    /**
     * Summary of adaptive teaching data from Phase 7 (optional).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdaptiveTeachingSummary {
        private int totalAdaptations;
        private List<String> conceptsNeedingReteaching;
        private List<String> misconceptionsDetected;
    }
}
