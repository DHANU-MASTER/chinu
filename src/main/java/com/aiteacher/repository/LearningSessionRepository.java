package com.aiteacher.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aiteacher.entity.LearningSession;

/**
 * Phase 11 repository for accessing learning session data.
 * Provides methods to query student progress and history.
 */
@Repository
public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

    /**
     * Find all sessions for a student, ordered by completion date descending.
     */
    List<LearningSession> findByStudentNameOrderByCompletedAtDesc(String studentName);

    /**
     * Find all sessions for a student by topic.
     */
    List<LearningSession> findByStudentNameAndTopicOrderByCompletedAtDesc(String studentName, String topic);

    /**
     * Count sessions for a student.
     */
    long countByStudentName(String studentName);
}
