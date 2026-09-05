package com.aiteacher.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lesson plan response generated from the student's actual profile input.
 * The profile fields echo the user's real values; the lesson content comes
 * from the AI provider's validated response — never from hardcoded content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonPlanResponse {

	private String studentName;

	private String topic;

	private String language;

	private String educationLevel;

	private String teachingStyle;

	private String objective;

	/** Echo of the student's optional prior knowledge (affects the generated lesson). */
	private String priorKnowledge;

	/** Echo of the student's optional available study time (affects the generated lesson). */
	private String availableTime;

	/** Echo of the student's optional desired depth (affects the generated lesson). */
	private String desiredDepth;

	private String lessonTitle;

	private String introduction;

	private List<String> learningObjectives;

	private List<LessonSection> sections;

	private int estimatedMinutes;

	/** One section of the generated lesson plan. */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class LessonSection {

		private String title;

		/** Merged explanation + example text (kept for the Phase 2/3 preview). */
		private String description;

		/** The AI's explanation text on its own. */
		private String explanation;

		/** The AI's example text on its own, or {@code null} when none was given. */
		private String example;

		/**
		 * Optional hint from the AI about the best generic visual treatment
		 * (equation, process, timeline, code, diagram). Purely decorative — the
		 * frontend must never invent facts from it.
		 */
		private String visualHint;

	}
}