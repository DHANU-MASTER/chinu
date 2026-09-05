package com.aiteacher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Phase 1 student profile intake. Carries real user input only — no
 * persistence and no AI processing happen in this phase.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileRequest {

	private String name;

	private String educationLevel;

	private String language;

	private String teachingStyle;

	private String objective;

	private String topic;

	/**
	 * Optional prior knowledge of the topic. When provided, the AI builds on it
	 * instead of re-explaining what the student already knows.
	 */
	private String priorKnowledge;

	/**
	 * Optional available study time (e.g. "10 minutes", "30 minutes"). When
	 * provided, the AI adapts the lesson length and density to it.
	 */
	private String availableTime;

	/**
	 * Optional desired depth (e.g. "Quick", "Standard", "Deep"). When provided,
	 * the AI matches the depth of the generated lesson to it.
	 */
	private String desiredDepth;

	/**
	 * Optional raw text of uploaded learning material (Phase 4). When present,
	 * it is the primary source for lesson generation and the topic may be left
	 * empty.
	 */
	private String uploadedMaterial;

	/** Optional student avatar profile pic icon or Base64 image URL. */
	private String profilePic;

}