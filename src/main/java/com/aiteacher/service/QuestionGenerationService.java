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

    /**
     * Streaming variant: forwards content deltas to the listener while the
     * question is being written, then returns the validated question.
     *
     * @param request the lesson context for question generation
     * @param onDelta invoked once per received content delta
     * @return a dynamically generated question (MCQ or SHORT_ANSWER)
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    default QuestionResponse generateQuestionStreaming(QuestionRequest request,
            java.util.function.Consumer<String> onDelta) {
        throw new UnsupportedOperationException("Streaming is not supported by this question generation service.");
    }
}
