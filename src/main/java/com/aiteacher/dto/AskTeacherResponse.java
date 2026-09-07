package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The teacher's persona-flavored answer to a student's mid-lesson question.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AskTeacherResponse {
    private String answer;
}
