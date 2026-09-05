package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 8 response containing the generated assessment.
 * Questions are dynamically generated from actual lesson content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResponse {

    /** Unique identifier for this assessment. */
    private String assessmentId;

    /** The topic being assessed. */
    private String topic;

    /** The language of the assessment. */
    private String language;

    /** The educational level. */
    private String educationLevel;

    /** List of dynamically generated questions. */
    private List<AssessmentQuestion> questions;

    /** Number of questions in the assessment. */
    private int totalQuestions;
}
