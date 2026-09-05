package com.aiteacher.service;

import com.aiteacher.dto.AssessmentRequest;
import com.aiteacher.dto.AssessmentResponse;
import com.aiteacher.dto.AssessmentResult;
import com.aiteacher.dto.AssessmentSubmissionRequest;

/**
 * Phase 8 contract for assessment generation and evaluation.
 * Questions are dynamically generated from actual lesson content.
 * Evaluations use AI semantic understanding — never fake scores.
 */
public interface AssessmentService {

    /**
     * Generates an assessment based on the actual lesson content and student profile.
     *
     * @param request the lesson content and student profile
     * @return generated assessment with dynamic questions
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    AssessmentResponse generateAssessment(AssessmentRequest request);

    /**
     * Evaluates the student's assessment answers using AI.
     * Returns structured results with score, concepts understood, weak areas, etc.
     *
     * @param request the student's answers and original questions
     * @return evaluation result with score and recommendations
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    AssessmentResult evaluateAssessment(AssessmentSubmissionRequest request);
}
