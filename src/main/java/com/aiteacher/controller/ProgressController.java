package com.aiteacher.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiteacher.dto.ProgressSaveRequest;
import com.aiteacher.dto.ProgressSummary;
import com.aiteacher.entity.LearningSession;
import com.aiteacher.service.ProgressService;

/**
 * Phase 11 REST API for student progress and learning history.
 * Stores and retrieves actual learning session data.
 * No hardcoded data — everything comes from real sessions.
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    private static final String SAVE_FAILURE_MESSAGE = "Unable to save learning session. Please try again.";
    private static final String HISTORY_FAILURE_MESSAGE = "Unable to load learning history. Please try again.";
    private static final String SUMMARY_FAILURE_MESSAGE = "Unable to load progress summary. Please try again.";

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    /**
     * POST /api/progress — saves a completed learning session.
     * Accepts the REAL assessment/report data from the student.
     */
    @PostMapping
    public ResponseEntity<?> saveSession(@RequestBody ProgressSaveRequest request) {
        try {
            if (isBlank(request.getStudentName())) {
                return badRequest("studentName must not be empty");
            }
            if (isBlank(request.getTopic())) {
                return badRequest("topic must not be empty");
            }

            LearningSession session = progressService.saveSession(request);
            return ResponseEntity.ok(session);
        } catch (Exception ex) {
            log.error("Failed to save learning session", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", SAVE_FAILURE_MESSAGE));
        }
    }

    /**
     * GET /api/progress — returns actual saved learning sessions for a student.
     */
    @GetMapping
    public ResponseEntity<?> getHistory(@RequestParam String studentName) {
        try {
            if (isBlank(studentName)) {
                return badRequest("studentName must not be empty");
            }

            List<LearningSession> history = progressService.getStudentHistory(studentName);
            return ResponseEntity.ok(history);
        } catch (Exception ex) {
            log.error("Failed to load learning history", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", HISTORY_FAILURE_MESSAGE));
        }
    }

    /**
     * GET /api/progress/summary — returns dynamically calculated progress summary.
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(@RequestParam String studentName) {
        try {
            if (isBlank(studentName)) {
                return badRequest("studentName must not be empty");
            }

            ProgressSummary summary = progressService.getProgressSummary(studentName);
            return ResponseEntity.ok(summary);
        } catch (Exception ex) {
            log.error("Failed to load progress summary", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", SUMMARY_FAILURE_MESSAGE));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
