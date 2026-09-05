package com.aiteacher.service;

import com.aiteacher.dto.QuestionRequest;
import com.aiteacher.dto.QuestionResponse;

/**
 * Phase 6 contract for AI-powered question generation.
 * Generates questions from actual lesson content — never from a
 * hardcoded question bank.
 */
public interface QuestionGenerationService {

    /**
     * Generates a single question based on the current lesson section.
     *
     * @param request the lesson context for question generation
     * @return a dynamically generated question (MCQ or SHORT_ANSWER)
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    QuestionResponse generateQuestion(QuestionRequest request);
}
