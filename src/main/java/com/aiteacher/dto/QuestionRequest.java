package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 6 request to generate a question from lesson content.
 * Carries the current lesson context so the AI can produce a
 * relevant question based on what was just taught.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {

    private String topic;

    private String language;

    private String educationLevel;

    private String teachingStyle;	private String objective;

	/** Optional prior knowledge, used to tune question difficulty. */
	private String priorKnowledge;

	/** Optional desired depth (Quick/Standard/Deep), used to tune question difficulty. */
	private String desiredDepth;

	/** The title of the current lesson section. */
    private String sectionTitle;

    /** The explanation content of the current lesson section. */
    private String sectionContent;

    /** The example content of the current lesson section, if any. */
    private String sectionExample;
}
