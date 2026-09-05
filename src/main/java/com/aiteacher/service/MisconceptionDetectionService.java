package com.aiteacher.service;

import com.aiteacher.dto.MisconceptionRequest;
import com.aiteacher.dto.MisconceptionResponse;

/**
 * Phase 7 contract for AI-powered misconception detection.
 * Analyzes the student's answer against the correct concept to identify
 * the type of understanding and any specific misconceptions.
 * Uses semantic AI evaluation — never keyword matching.
 */
public interface MisconceptionDetectionService {

    /**
     * Detects the student's understanding level and identifies any misconceptions.
     *
     * @param request the full context: question, answer, lesson, student profile
     * @return understanding level and misconception analysis
     * @throws com.aiteacher.ai.AiException when the AI call fails
     */
    MisconceptionResponse detectMisconception(MisconceptionRequest request);
}
