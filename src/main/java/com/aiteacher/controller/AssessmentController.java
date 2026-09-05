package com.aiteacher.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiteacher.ai.AiException;
import com.aiteacher.dto.AssessmentRequest;
import com.aiteacher.dto.AssessmentResponse;
import com.aiteacher.dto.AssessmentResult;
import com.aiteacher.dto.AssessmentSubmissionRequest;
import com.aiteacher.service.AssessmentService;

/**
 * Phase 8 REST API for final assessment: generates dynamic questions from actual
 * lesson content and evaluates student answers. No hardcoded questions, no fake
 * scores — every assessment is driven by real data.
 */
@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentController.class);

    private static final String UNAVAILABLE_MESSAGE =
            "AI assessment is not configured yet. Set the AI_API_KEY environment variable and restart, then try again.";
    private static final String GENERATE_FAILURE_MESSAGE = "Unable to generate the assessment. Please try again.";
    private static final String EVALUATE_FAILURE_MESSAGE = "Unable to evaluate the assessment. Please try again.";

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /**
     * POST /api/assessment/generate — generates an assessment from the actual
     * lesson content. The request must carry the real lesson data.
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateAssessment(@RequestBody AssessmentRequest request) {
        try {
            if (isBlank(request.getTopic()) && isBlank(request.getLessonTitle())) {
                return badRequest("topic or lessonTitle must not be empty");
            }
            if (isBlank(request.getLanguage())) {
                return badRequest("language must not be empty");
            }
            if (isBlank(request.getEducationLevel())) {
                return badRequest("educationLevel must not be empty");
            }
            if (request.getSections() == null || request.getSections().isEmpty()) {
                return badRequest("sections must not be empty");
            }
            if (request.getQuestionCount() < 1 || request.getQuestionCount() > 20) {
                request.setQuestionCount(5); // default to 5 if invalid
            }

            AssessmentResponse response = assessmentService.generateAssessment(request);
            return ResponseEntity.ok(response);
        } catch (AiException ex) {
            log.warn("Assessment generation failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : GENERATE_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during assessment generation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", GENERATE_FAILURE_MESSAGE));
        }
    }

    /**
     * POST /api/assessment/submit — evaluates the student's assessment answers
     * and returns a learning report.
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitAssessment(@RequestBody AssessmentSubmissionRequest request) {
        try {
            if (isBlank(request.getAssessmentId())) {
                return badRequest("assessmentId must not be empty");
            }
            if (isBlank(request.getTopic())) {
                return badRequest("topic must not be empty");
            }
            if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
                return badRequest("questions must not be empty");
            }
            if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
                return badRequest("answers must not be empty");
            }
            if (request.getQuestions().size() != request.getAnswers().size()) {
                return badRequest("questions and answers must have the same count");
            }

            AssessmentResult result = assessmentService.evaluateAssessment(request);
            return ResponseEntity.ok(result);
        } catch (AiException ex) {
            log.warn("Assessment evaluation failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : EVALUATE_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during assessment evaluation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", EVALUATE_FAILURE_MESSAGE));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
