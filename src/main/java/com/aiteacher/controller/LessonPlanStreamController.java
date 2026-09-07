package com.aiteacher.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aiteacher.ai.AiException;
import com.aiteacher.dto.LessonPlanResponse;
import com.aiteacher.dto.StudentProfileRequest;
import com.aiteacher.service.AILessonService;

/**
 * SSE variant of lesson generation: {@code POST /api/lesson/plan/stream}.
 *
 * <p>Event sequence:
 * <ol>
 *   <li>{@code start} — the request was accepted, generation is beginning.</li>
 *   <li>{@code delta} — one content token from the AI, forwarded live.</li>
 *   <li>{@code plan} — the fully validated {@link LessonPlanResponse} JSON.</li>
 *   <li>{@code error} — a safe, user-presentable failure message.</li>
 * </ol>
 *
 * <p>The POST verb is deliberate: the browser consumes this endpoint with
 * {@code fetch()} + ReadableStream (EventSource only supports GET).
 */
@RestController
@RequestMapping("/api")
public class LessonPlanStreamController {

	private static final Logger log = LoggerFactory.getLogger(LessonPlanStreamController.class);

	private static final String UNAVAILABLE_MESSAGE =
			"AI lesson generation is not configured yet. Set the AI_API_KEY environment variable and restart, then try again.";
	private static final String GENERIC_FAILURE_MESSAGE = "Unable to prepare the lesson. Please try again.";

	private final AILessonService lessonService;

	public LessonPlanStreamController(AILessonService lessonService) {
		this.lessonService = lessonService;
	}

	@PostMapping(value = "/lesson/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamLessonPlan(@RequestBody StudentProfileRequest request) {
		SseEmitter emitter = new SseEmitter(240_000L);

		// Run generation off the request thread so deltas can be pushed as they arrive.
		Thread worker = new Thread(() -> {
			try {
				StudentProfileRequest cleaned = cleanProfile(request);
				send(emitter, "start", Map.of("status", "generating"));

				LessonPlanResponse plan = lessonService.generateLessonStreaming(cleaned, delta -> {
					try {
						send(emitter, "delta", Map.of("text", delta));
					} catch (Exception ex) {
						log.debug("Client disconnected during lesson stream", ex);
					}
				});

				send(emitter, "plan", plan);
				emitter.complete();
			} catch (IllegalArgumentException ex) {
				completeWithError(emitter, ex.getMessage());
			} catch (AiException ex) {
				log.warn("Streaming lesson generation failed ({}): {}", ex.getKind(), ex.getMessage());
				completeWithError(emitter, ex.isUnavailable() ? UNAVAILABLE_MESSAGE : GENERIC_FAILURE_MESSAGE);
			} catch (Exception ex) {
				log.error("Unexpected error during streaming lesson generation", ex);
				completeWithError(emitter, GENERIC_FAILURE_MESSAGE);
			}
		}, "lesson-plan-stream");
		worker.setDaemon(true);
		worker.start();

		return emitter;
	}

	private void send(SseEmitter emitter, String event, Object data) throws IOException {
		synchronized (emitter) {
			emitter.send(SseEmitter.event().name(event).data(data));
		}
	}

	private void completeWithError(SseEmitter emitter, String message) {
		try {
			send(emitter, "error", Map.of("error", message));
			emitter.complete();
		} catch (Exception ex) {
			emitter.completeWithError(ex);
		}
	}

	/** Same cleaning/validation contract as POST /api/lesson/plan. */
	private StudentProfileRequest cleanProfile(StudentProfileRequest request) {
		String material = clean(request.getUploadedMaterial());
		boolean hasMaterial = material != null;
		return StudentProfileRequest.builder()
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
	}

	private static String require(String value, String field) {
		String cleaned = clean(value);
		if (cleaned == null) {
			throw new IllegalArgumentException(field + " must not be empty");
		}
		return cleaned;
	}

	private static String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
