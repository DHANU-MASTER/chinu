package com.aiteacher.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aiteacher.ai.AiException;
import com.aiteacher.dto.AskTeacherRequest;
import com.aiteacher.dto.AskTeacherResponse;
import com.aiteacher.dto.AdaptiveTeachingRequest;
import com.aiteacher.dto.AdaptiveTeachingResponse;
import com.aiteacher.dto.EvaluationRequest;
import com.aiteacher.dto.EvaluationResponse;
import com.aiteacher.dto.MisconceptionRequest;
import com.aiteacher.dto.MisconceptionResponse;
import com.aiteacher.dto.QuestionRequest;
import com.aiteacher.dto.QuestionResponse;
import com.aiteacher.service.AdaptiveTeachingService;
import com.aiteacher.service.AskTeacherService;
import com.aiteacher.service.AnswerEvaluationService;
import com.aiteacher.service.MisconceptionDetectionService;
import com.aiteacher.service.QuestionGenerationService;

/**
 * Phase 6+7 REST API for student interaction: generates questions from actual
 * lesson content, evaluates real student answers, detects misconceptions, and
 * provides adaptive re-teaching. No hardcoded questions, no fake evaluation —
 * every interaction is driven by real data.
 */
@RestController
@RequestMapping("/api/lesson")
public class LessonInteractionController {

    private static final Logger log = LoggerFactory.getLogger(LessonInteractionController.class);

    private static final String UNAVAILABLE_MESSAGE =
            "AI interaction is not configured yet. Set the AI_API_KEY environment variable and restart, then try again.";
    private static final String QUESTION_FAILURE_MESSAGE = "Unable to generate a question. Please retry.";
    private static final String EVALUATION_FAILURE_MESSAGE = "Unable to evaluate your answer. Please try again.";
    private static final String MISCONCEPTION_FAILURE_MESSAGE = "Unable to analyze your answer right now. Please try again.";
    private static final String ADAPT_FAILURE_MESSAGE = "Unable to generate adaptive explanation. Please try again.";

    private static final String ASK_FAILURE_MESSAGE = "The teacher could not answer right now. Please try again.";

    private final QuestionGenerationService questionService;
    private final AnswerEvaluationService evaluationService;
    private final MisconceptionDetectionService misconceptionService;
    private final AdaptiveTeachingService adaptiveService;
    private final AskTeacherService askTeacherService;

    public LessonInteractionController(QuestionGenerationService questionService,
            AnswerEvaluationService evaluationService,
            MisconceptionDetectionService misconceptionService,
            AdaptiveTeachingService adaptiveService,
            AskTeacherService askTeacherService) {
        this.questionService = questionService;
        this.evaluationService = evaluationService;
        this.misconceptionService = misconceptionService;
        this.adaptiveService = adaptiveService;
        this.askTeacherService = askTeacherService;
    }

    /**
     * POST /api/lesson/ask — the student asks a free-form follow-up question
     * mid-lesson; the current persona answers grounded in the section content.
     */
    @PostMapping("/ask")
    public ResponseEntity<?> askTeacher(@RequestBody AskTeacherRequest request) {
        try {
            if (isBlank(request.getQuestion())) {
                return badRequest("question must not be empty");
            }
            if (request.getQuestion().length() > 500) {
                return badRequest("question must be 500 characters or fewer");
            }
            AskTeacherResponse response = askTeacherService.answerQuestion(request);
            return ResponseEntity.ok(response);
        } catch (AiException ex) {
            log.warn("Ask-teacher failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : ASK_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during ask-teacher", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ASK_FAILURE_MESSAGE));
        }
    }

    /**
     * POST /api/lesson/question — generates a question from the current
     * lesson section. The request must carry the actual section content.
     */
    @PostMapping("/question")
    public ResponseEntity<?> generateQuestion(@RequestBody QuestionRequest request) {
        try {
            if (isBlank(request.getTopic())) {
                return badRequest("topic must not be empty");
            }
            if (isBlank(request.getSectionTitle())) {
                return badRequest("sectionTitle must not be empty");
            }
            if (isBlank(request.getSectionContent())) {
                return badRequest("sectionContent must not be empty");
            }
            if (isBlank(request.getLanguage())) {
                return badRequest("language must not be empty");
            }
            if (isBlank(request.getEducationLevel())) {
                return badRequest("educationLevel must not be empty");
            }

            QuestionResponse question = questionService.generateQuestion(request);
            return ResponseEntity.ok(question);
        } catch (AiException ex) {
            log.warn("Question generation failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : QUESTION_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during question generation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", QUESTION_FAILURE_MESSAGE));
        }
    }

    /**
     * POST /api/lesson/question/stream — SSE variant of question generation.
     * Emits: {@code start}, {@code delta} (per token), {@code question} (the
     * validated QuestionResponse), or {@code error}. Consumed with fetch() +
     * ReadableStream so the check question types itself in live.
     */
    @PostMapping(value = "/question/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuestion(@RequestBody QuestionRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        Thread worker = new Thread(() -> {
            try {
                validateQuestionRequest(request);
                sendEvent(emitter, "start", Map.of("status", "generating"));

                QuestionResponse question = questionService.generateQuestionStreaming(request, delta -> {
                    try {
                        sendEvent(emitter, "delta", Map.of("text", delta));
                    } catch (Exception ex) {
                        log.debug("Client disconnected during question stream", ex);
                    }
                });

                sendEvent(emitter, "question", question);
                emitter.complete();
            } catch (IllegalArgumentException ex) {
                completeEmitterWithError(emitter, ex.getMessage());
            } catch (AiException ex) {
                log.warn("Question streaming failed ({}): {}", ex.getKind(), ex.getMessage());
                completeEmitterWithError(emitter,
                        ex.isUnavailable() ? UNAVAILABLE_MESSAGE : QUESTION_FAILURE_MESSAGE);
            } catch (Exception ex) {
                log.error("Unexpected error during question streaming", ex);
                completeEmitterWithError(emitter, QUESTION_FAILURE_MESSAGE);
            }
        }, "question-stream");
        worker.setDaemon(true);
        worker.start();

        return emitter;
    }

    private void validateQuestionRequest(QuestionRequest request) {
        if (isBlank(request.getTopic())) {
            throw new IllegalArgumentException("topic must not be empty");
        }
        if (isBlank(request.getSectionTitle())) {
            throw new IllegalArgumentException("sectionTitle must not be empty");
        }
        if (isBlank(request.getSectionContent())) {
            throw new IllegalArgumentException("sectionContent must not be empty");
        }
        if (isBlank(request.getLanguage())) {
            throw new IllegalArgumentException("language must not be empty");
        }
        if (isBlank(request.getEducationLevel())) {
            throw new IllegalArgumentException("educationLevel must not be empty");
        }
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) throws IOException {
        synchronized (emitter) {
            emitter.send(SseEmitter.event().name(event).data(data));
        }
    }

    private void completeEmitterWithError(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "error", Map.of("error", message));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    /**
     * POST /api/lesson/evaluate — evaluates a student's answer using AI
     * semantic understanding.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluateAnswer(@RequestBody EvaluationRequest request) {
        try {
            if (isBlank(request.getQuestion())) {
                return badRequest("question must not be empty");
            }
            if (isBlank(request.getStudentAnswer())) {
                return badRequest("studentAnswer must not be empty");
            }
            if (isBlank(request.getLessonSection())) {
                return badRequest("lessonSection must not be empty");
            }
            if (isBlank(request.getTopic())) {
                return badRequest("topic must not be empty");
            }

            EvaluationResponse evaluation = evaluationService.evaluateAnswer(request);
            return ResponseEntity.ok(evaluation);
        } catch (AiException ex) {
            log.warn("Answer evaluation failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : EVALUATION_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during answer evaluation", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", EVALUATION_FAILURE_MESSAGE));
        }
    }

    /**
     * POST /api/lesson/misconception — detects the student's understanding
     * level and identifies any misconceptions from their answer.
     */
    @PostMapping("/misconception")
    public ResponseEntity<?> detectMisconception(@RequestBody MisconceptionRequest request) {
        try {
            if (isBlank(request.getQuestion())) {
                return badRequest("question must not be empty");
            }
            if (isBlank(request.getStudentAnswer())) {
                return badRequest("studentAnswer must not be empty");
            }
            if (isBlank(request.getCorrectConcept())) {
                return badRequest("correctConcept must not be empty");
            }
            if (isBlank(request.getLessonSection())) {
                return badRequest("lessonSection must not be empty");
            }
            if (isBlank(request.getTopic())) {
                return badRequest("topic must not be empty");
            }
            if (isBlank(request.getLanguage())) {
                return badRequest("language must not be empty");
            }
            if (isBlank(request.getEducationLevel())) {
                return badRequest("educationLevel must not be empty");
            }

            MisconceptionResponse response = misconceptionService.detectMisconception(request);
            return ResponseEntity.ok(response);
        } catch (AiException ex) {
            log.warn("Misconception detection failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : MISCONCEPTION_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during misconception detection", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", MISCONCEPTION_FAILURE_MESSAGE));
        }
    }

    /**
     * POST /api/lesson/adapt — generates an adaptive re-teaching response
     * when the student is struggling with a concept.
     */
    @PostMapping("/adapt")
    public ResponseEntity<?> generateAdaptation(@RequestBody AdaptiveTeachingRequest request) {
        try {
            if (isBlank(request.getUnderstanding())) {
                return badRequest("understanding must not be empty");
            }
            if (isBlank(request.getOriginalExplanation())) {
                return badRequest("originalExplanation must not be empty");
            }
            if (isBlank(request.getTopic())) {
                return badRequest("topic must not be empty");
            }
            if (isBlank(request.getLanguage())) {
                return badRequest("language must not be empty");
            }
            if (isBlank(request.getEducationLevel())) {
                return badRequest("educationLevel must not be empty");
            }
            if (isBlank(request.getQuestion())) {
                return badRequest("question must not be empty");
            }
            if (isBlank(request.getStudentAnswer())) {
                return badRequest("studentAnswer must not be empty");
            }

            AdaptiveTeachingResponse response = adaptiveService.generateAdaptation(request);
            return ResponseEntity.ok(response);
        } catch (AiException ex) {
            log.warn("Adaptive teaching failed ({}): {}", ex.getKind(), ex.getMessage());
            String message = ex.isUnavailable() ? UNAVAILABLE_MESSAGE : ADAPT_FAILURE_MESSAGE;
            HttpStatus status = ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(Map.of("error", message));
        } catch (Exception ex) {
            log.error("Unexpected error during adaptive teaching", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ADAPT_FAILURE_MESSAGE));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
