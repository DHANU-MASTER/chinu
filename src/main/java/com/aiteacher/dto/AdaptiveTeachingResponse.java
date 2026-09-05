package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 7 response from adaptive re-teaching.
 * Contains the adapted explanation, new example/analogy, and a
 * follow-up question to verify understanding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveTeachingResponse {

    /** The teaching approach used: SIMPLER, EXAMPLE_BASED, ANALOGY, STEP_BY_STEP, VISUAL, PRACTICAL. */
    private String adaptationType;

    /** Brief description of the misconception being addressed. */
    private String misconception;

    /** The new, adapted explanation using a different approach. */
    private String reExplanation;

    /** A new example or analogy to illustrate the concept differently. */
    private String example;

    /** A follow-up question to verify the student now understands. */
    private String followUpQuestion;

    /** The type of the follow-up question: MCQ or SHORT_ANSWER. */
    private String followUpQuestionType;

    /** For MCQ follow-up: the list of answer options. */
    private java.util.List<String> followUpOptions;

    /** For MCQ follow-up: the zero-based index of the correct option. */
    private Integer followUpCorrectOptionIndex;
}
