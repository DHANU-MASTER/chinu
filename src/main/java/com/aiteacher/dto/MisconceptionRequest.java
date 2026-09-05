package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 7 request to detect misconceptions from a student's answer.
 * Carries the full context: question, correct concept, student answer,
 * lesson section, and student profile for semantic AI analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisconceptionRequest {

    /** The question that was asked. */
    private String question;

    /** The question type: MCQ or SHORT_ANSWER. */
    private String questionType;

    /** The student's actual answer text. */
    private String studentAnswer;

    /** The correct concept or expected answer. */
    private String correctConcept;

    /** The evaluation status from Phase 6: CORRECT, PARTIALLY_CORRECT, INCORRECT. */
    private String evaluationStatus;

    /** The evaluation feedback from Phase 6. */
    private String evaluationFeedback;

    /** The lesson section content. */
    private String lessonSection;

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
}
