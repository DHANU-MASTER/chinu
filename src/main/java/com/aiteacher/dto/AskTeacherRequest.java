package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for a student's free-form follow-up question asked mid-lesson.
 * The teacher answers in persona, grounded in the current section content.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AskTeacherRequest {
    private String question;
    private String topic;
    private String sectionTitle;
    private String sectionContent;
    private String persona;
    private String language;
}
