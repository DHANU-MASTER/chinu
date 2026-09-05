package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 7 response from misconception detection.
 * Contains the understanding level, identified misconception,
 * severity, and whether adaptive re-teaching is needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisconceptionResponse {

    /**
     * Understanding level: UNDERSTOOD, PARTIAL, MISCONCEPTION, NOT_UNDERSTOOD.
     * - UNDERSTOOD: Student demonstrated clear understanding.
     * - PARTIAL: Student shows some understanding but is incomplete.
     * - MISCONCEPTION: Student has a specific incorrect mental model.
     * - NOT_UNDERSTOOD: Student does not understand the concept at all.
     */
    private String understanding;

    /** Description of the identified misconception (null when UNDERSTOOD). */
    private String misconception;

    /** Severity of the misconception: LOW, MEDIUM, HIGH. */
    private String severity;

    /** Whether adaptive re-teaching is needed. */
    private boolean explanationNeeded;

    /** The teaching approach that would work best for this student's issue. */
    private String recommendedApproach;
}
