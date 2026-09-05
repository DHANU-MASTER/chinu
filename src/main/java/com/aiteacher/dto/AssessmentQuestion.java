package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 8 individual assessment question.
 * Can be MCQ or SHORT_ANSWER, dynamically generated from actual lesson content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestion {

    /** Question type: MCQ, SHORT_ANSWER, or APPLICATION. */
    private String type;

    /** The question text. */
    private String question;

    /** For MCQ: list of answer options. */
    private List<String> options;

    /** For MCQ: zero-based index of the correct option. */
    private Integer correctOptionIndex;

    /** The concept this question tests. */
    private String concept;

    /** Brief explanation of the correct answer (for feedback). */
    private String explanation;
}
