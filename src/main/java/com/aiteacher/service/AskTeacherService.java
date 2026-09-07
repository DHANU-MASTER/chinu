package com.aiteacher.service;

import com.aiteacher.dto.AskTeacherRequest;
import com.aiteacher.dto.AskTeacherResponse;

/**
 * Answers free-form follow-up questions a student asks mid-lesson,
 * grounded in the section being taught and delivered in the persona's voice.
 */
public interface AskTeacherService {

    AskTeacherResponse answerQuestion(AskTeacherRequest request);

    /**
     * Streaming variant: forwards content deltas while the answer is being
     * written, then returns the final answer. Used by the WebSocket chat.
     *
     * @param request the question plus lesson context and prior turns
     * @param onDelta invoked once per received content delta
     * @return the completed answer
     */
    default AskTeacherResponse generateAnswerStreaming(AskTeacherRequest request,
            java.util.function.Consumer<String> onDelta) {
        throw new UnsupportedOperationException("Streaming is not supported by this ask-teacher service.");
    }
}
