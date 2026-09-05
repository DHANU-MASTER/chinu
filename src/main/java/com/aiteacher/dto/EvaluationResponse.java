package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 6 response from AI-based answer evaluation.
 * Contains the evaluation status, personalized feedback, and
 * the expected concept for learning reinforcement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {

    /** Evaluation status: CORRECT, PARTIALLY_CORRECT, or INCORRECT. */
    private String status;

    /** Teacher-style feedback explaining why the answer is correct or incorrect. */
    private String feedback;

    /** The key concept the question was testing. */
    private String expectedConcept;

    /** AI confidence in its evaluation (0.0 to 1.0). */
    private double confidence;
}
