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
import com.aiteacher.dto.LessonPlanResponse;
import com.aiteacher.dto.StudentProfileRequest;
import com.aiteacher.service.AIService;

/**
 * Phase 3 REST API: sends the student's real profile to the AI lesson
 * generation service ({@link AIService}) and returns the validated, structured
 * lesson plan. All six profile fields are required, trimmed, and forwarded to
 * the AI verbatim — nothing is hardcoded and no value is silently defaulted.
 */
@RestController
@RequestMapping("/api")
public class LessonPlanController {

	private static final Logger log = LoggerFactory.getLogger(LessonPlanController.class);

	private static final String UNAVAILABLE_MESSAGE =
			"AI lesson generation is not configured yet. Set the AI_API_KEY environment variable and restart, then try again.";
	private static final String GENERIC_FAILURE_MESSAGE = "Unable to prepare the lesson. Please try again.";

	private final AIService aiService;

	public LessonPlanController(AIService aiService) {
		this.aiService = aiService;
	}

	/**
	 * POST /api/lesson/plan — validates the student profile, generates the lesson
	 * through the configured AI provider, and returns the structured plan.
	 * The topic is required only when no uploaded material is provided; in
	 * material mode the uploaded content is the source for the lesson.
	 */
	@PostMapping("/lesson/plan")
	public ResponseEntity<?> createLessonPlan(@RequestBody StudentProfileRequest request) {
		try {
			String material = clean(request.getUploadedMaterial());
			boolean hasMaterial = material != null;
			StudentProfileRequest cleaned = StudentProfileRequest.builder()
					.name(require(request.getName(), "name"))
					.educationLevel(require(request.getEducationLevel(), "educationLevel"))
					.language(require(request.getLanguage(), "language"))
					.teachingStyle(require(request.getTeachingStyle(), "teachingStyle"))
					.objective(require(request.getObjective(), "objective"))
					.topic(hasMaterial ? clean(request.getTopic()) : require(request.getTopic(), "topic"))
					.priorKnowledge(clean(request.getPriorKnowledge()))
					.availableTime(clean(request.getAvailableTime()))
					.desiredDepth(clean(request.getDesiredDepth()))
					.uploadedMaterial(material)
					.build();
			LessonPlanResponse plan = aiService.generateLesson(cleaned);
			return ResponseEntity.ok(plan);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
		} catch (AiException ex) {
			// Log implementation details server-side only; the client gets a safe message.
			log.warn("Lesson generation failed ({}): {}", ex.getKind(), ex.getMessage());
			HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
			String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : GENERIC_FAILURE_MESSAGE;
			return ResponseEntity.status(status).body(Map.of("error", message));
		}
	}

	private static String require(String value, String field) {
		String cleaned = clean(value);
		if (cleaned == null) {
			throw new IllegalArgumentException(field + " must not be empty");
		}
		return cleaned;
	}

	private static String clean(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
