package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 11 request for saving a completed learning session.
 * Contains the real student profile and assessment result data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressSaveRequest {

    /** Student's name from the profile. */
    private String studentName;

    /** The topic that was studied. */
    private String topic;

    /** Student's preferred language. */
    private String language;

    /** Student's educational level. */
    private String educationalLevel;

    /** Student's teaching style preference. */
    private String teachingStyle;

    /** Student's learning objective. */
    private String learningObjective;

    /** Assessment score (number of correct answers). */
    private int score;

    /** Total number of questions in the assessment. */
    private int totalQuestions;

    /** Score as percentage (0-100). */
    private double percentage;

    /** Concepts the student demonstrated understanding of. */
    private List<String> conceptsUnderstood;

    /** Areas where the student needs more practice. */
    private List<String> weakAreas;

    /** Concepts the student answered incorrectly. */
    private List<String> incorrectConcepts;

    /** Revision recommendations from the assessment. */
    private List<String> revisionRecommendations;

    /** AI-generated suggested next topic. */
    private String suggestedNextTopic;
}
