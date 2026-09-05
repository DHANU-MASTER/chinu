package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 7 request for adaptive re-teaching. Carries the misconception
 * analysis and lesson context so the AI can generate a different
 * explanation approach.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveTeachingRequest {

    /** The understanding level from misconception detection. */
    private String understanding;

    /** The identified misconception. */
    private String misconception;

    /** The recommended teaching approach. */
    private String recommendedApproach;

    /** The original lesson section content. */
    private String originalExplanation;

    /** The original example from the lesson. */
    private String originalExample;

    /** The section title. */
    private String sectionTitle;

    /** The topic of the lesson. */
    private String topic;

    /** The student's preferred language. */
    private String language;

    /** The student's education level. */
    private String educationLevel;

    /** The student's teaching style preference. */
    private String teachingStyle;

    /** The student's learning objective. */
    private String objective;

    /** The student's answer that triggered this adaptation. */
    private String studentAnswer;

    /** The question that was asked. */
    private String question;

    /** How many times we've already adapted for this concept (0 = first adaptation). */
    private int adaptationCount;
}
