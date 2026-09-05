package com.aiteacher.service;

import com.aiteacher.dto.AdaptiveTeachingRequest;
import com.aiteacher.dto.AdaptiveTeachingResponse;

/**
 * Phase 7 contract for AI-powered adaptive re-teaching.
 * When a student struggles, generates a different explanation using
 * a new teaching approach (analogy, step-by-step, example-based, etc.)
 * and creates a follow-up question to verify understanding.
 */
public interface AdaptiveTeachingService {

    /**
     * Generates an adapted explanation and follow-up question.
     *
     * @param request the misconception analysis and lesson context
     * @return adapted explanation with new approach and follow-up question
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    AdaptiveTeachingResponse generateAdaptation(AdaptiveTeachingRequest request);
}
