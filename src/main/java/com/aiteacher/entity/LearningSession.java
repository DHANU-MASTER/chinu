package com.aiteacher.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 11 entity representing a completed learning session.
 * Stores the actual student's learning progress, assessment results,
 * and recommendations from the real lesson/assessment flow.
 */
@Entity
@Table(name = "learning_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Student's name from the profile. */
    @Column(nullable = false)
    private String studentName;

    /** The topic that was studied. */
    @Column(nullable = false)
    private String topic;

    /** Student's preferred language. */
    @Column(nullable = false)
    private String language;

    /** Student's educational level. */
    @Column(nullable = false)
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
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "concept")
    private List<String> conceptsUnderstood;

    /** Areas where the student needs more practice. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "weak_area")
    private List<String> weakAreas;

    /** Concepts the student answered incorrectly. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "incorrect_concept")
    private List<String> incorrectConcepts;

    /** Revision recommendations from the assessment. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "revision_recommendation")
    private List<String> revisionRecommendations;

    /** AI-generated suggested next topic. */
    private String suggestedNextTopic;

    /** When the session was completed. */
    @Column(nullable = false)
    private LocalDateTime completedAt;
}
