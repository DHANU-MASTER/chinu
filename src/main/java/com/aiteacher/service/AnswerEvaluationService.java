package com.aiteacher.service;

import com.aiteacher.dto.EvaluationRequest;
import com.aiteacher.dto.EvaluationResponse;

/**
 * Phase 6 contract for AI-powered answer evaluation.
 * Evaluates student answers semantically using AI — never by simple
 * string comparison (except for MCQ option matching).
 */
public interface AnswerEvaluationService {

    /**
     * Evaluates a student's answer using AI semantic understanding.
     *
     * @param request the question context and student's answer
     * @return evaluation result with status, feedback, and confidence
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    EvaluationResponse evaluateAnswer(EvaluationRequest request);
}
