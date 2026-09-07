package com.aiteacher.service;

import com.aiteacher.dto.AskTeacherRequest;
import com.aiteacher.dto.AskTeacherResponse;

/**
 * Answers free-form follow-up questions a student asks mid-lesson,
 * grounded in the section being taught and delivered in the persona's voice.
 */
public interface AskTeacherService {

    AskTeacherResponse answerQuestion(AskTeacherRequest request);
}
