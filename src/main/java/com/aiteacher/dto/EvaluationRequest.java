package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 6 request to evaluate a student's answer.
 * Carries the question context and the student's actual response
 * so the AI can perform semantic evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {

    /** The question that was asked. */
    private String question;

    /** The question type: MCQ or SHORT_ANSWER. */
    private String questionType;

    /** The student's actual answer text. For MCQ this is the selected option text. */
    private String studentAnswer;

    /** For MCQ: the zero-based index of the correct option (set by backend). */
    private Integer correctOptionIndex;

    /** The lesson section content that the question was based on. */
    private String lessonSection;

    /** The topic of the lesson. */
    private String topic;

    /** The student's preferred language. */
    private String language;

    /** The student's education level. */
    private String educationLevel;
}
