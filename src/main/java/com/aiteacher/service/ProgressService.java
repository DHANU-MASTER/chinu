package com.aiteacher.service;

import java.util.List;

import com.aiteacher.dto.ProgressSaveRequest;
import com.aiteacher.dto.ProgressSummary;
import com.aiteacher.entity.LearningSession;

/**
 * Phase 11 contract for student progress and learning history.
 * Stores and retrieves actual learning session data.
 */
public interface ProgressService {

    /**
     * Saves a completed learning session.
     *
     * @param request the real student profile and assessment data
     * @return the saved learning session
     */
    LearningSession saveSession(ProgressSaveRequest request);

    /**
     * Gets all learning sessions for a student.
     *
     * @param studentName the student's name
     * @return list of learning sessions ordered by date descending
     */
    List<LearningSession> getStudentHistory(String studentName);

    /**
     * Gets the progress summary for a student.
     * Dynamically calculates average score, best score, weak areas, etc.
     *
     * @param studentName the student's name
     * @return progress summary with calculated values
     */
    ProgressSummary getProgressSummary(String studentName);
}
