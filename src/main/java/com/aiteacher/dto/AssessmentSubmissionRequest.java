package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 8 request for submitting student's assessment answers.
 * Contains the actual student answers to be evaluated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentSubmissionRequest {

    /** The assessment ID from the generation response. */
    private String assessmentId;

    /** The topic being assessed. */
    private String topic;

    /** Student's preferred language. */
    private String language;

    /** Student's educational level. */
    private String educationLevel;

    /** The original questions for reference. */
    private List<AssessmentQuestion> questions;

    /** Student's answers (same index as questions). */
    private List<StudentAnswer> answers;

    /** The lesson title for context. */
    private String lessonTitle;

    /**
     * Represents a single student answer.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentAnswer {
        /** The question text (for reference). */
        private String question;

        /** The student's answer text (option text for MCQ, typed text for SHORT_ANSWER). */
        private String answer;

        /** The question type. */
        private String questionType;

        /** For MCQ: the zero-based index of the selected option. */
        private Integer selectedOptionIndex;
    }
}
