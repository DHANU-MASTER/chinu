package com.aiteacher.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;	import com.aiteacher.dto.LessonPlanResponse;
	import com.aiteacher.dto.LessonPlanResponse.LessonSection;
	import com.aiteacher.dto.StudentProfileRequest;
	import com.aiteacher.rag.RAGService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 3 AI lesson generation. Turns the student's real profile into a prompt
 * that is sent to the configured AI provider (OpenAI-compatible endpoint, see
 * {@link com.aiteacher.ai.OpenAiCompatibleChatClient}), then validates the	 * provider's structured JSON answer before mapping it onto a
	 * {@link LessonPlanResponse}. Every value the student submitted is forwarded to
	 * the provider verbatim — nothing is hardcoded, defaulted or replaced. When the
	 * student uploads material, the most relevant excerpts are retrieved through the
	 * {@link RAGService} pipeline (chunk → embed → retrieve) instead of dumping the
	 * raw document into the prompt.
 *
 * <p>Configuration comes from environment variables only:
 * {@code AI_API_KEY}, {@code AI_BASE_URL}, {@code AI_MODEL},
 * {@code AI_TIMEOUT_SECONDS}. When no key is configured the service fails with a
 * typed {@link AiException#unavailable(String)} instead of fabricating a lesson.
 */
@Service
public class AILessonService implements AIService {

	private static final int MIN_ESTIMATED_MINUTES = 5;
	private static final int MAX_ESTIMATED_MINUTES = 60;

	/** Number of retrieved material excerpts grounded into the prompt. */
	private static final int MAX_RETRIEVED_CHUNKS = 4;

	/** Hard cap on the retrieved context sent to the AI, so the prompt stays bounded. */
	private static final int MAX_MATERIAL_CHARS = 8_000;

	private static final String OUTPUT_SCHEMA = """
			{
			  "lessonTitle": "...",
			  "introduction": "...",
			  "learningObjectives": ["...", "...", "..."],
			  "sections": [
			    { "title": "...", "explanation": "...", "example": "...", "visualHint": "equation" }
			  ],
			  "estimatedMinutes": 15
			}
			""";

	/** Source-grounding rule injected when the student uploads learning material. */
	private static final String MATERIAL_GUIDANCE = """
			Use the supplied learning material as the primary source.
			Do not invent facts that contradict the supplied material.
			If the material does not contain enough information to answer something,
			clearly state that additional information is required.
			When a fact or explanation comes directly from an excerpt of the material,
			cite it inline as [Excerpt N] (N = the excerpt number shown above the text).
			Use at most one citation per sentence, only where the source truly supports it.
			""";

	private final AiChatClient chatClient;
	private final ObjectMapper json;
	private final RAGService ragService;
	private final String apiKey;
	private final String baseUrl;
	private final String model;

	public AILessonService(AiChatClient chatClient, ObjectMapper json, RAGService ragService,
			@Value("${AI_API_KEY:}") String apiKey,
			@Value("${AI_BASE_URL:https://api.openai.com/v1}") String baseUrl,
			@Value("${AI_MODEL:gpt-4o-mini}") String model) {
		this.chatClient = chatClient;
		this.json = json;
		this.ragService = ragService;
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.model = model;
	}

	@Override
	public LessonPlanResponse generateLesson(StudentProfileRequest profile) {
		if (apiKey == null || apiKey.isBlank()) {
			throw AiException.unavailable("AI lesson generation is not configured: the AI_API_KEY environment variable is not set.");
		}

		String rawContent = chatClient.chatCompletion(baseUrl, apiKey.trim(), model, prepareMessages(profile));

		return parseLesson(profile, rawContent);
	}

	/**
	 * Streaming variant of {@link #generateLesson(StudentProfileRequest)}: the
	 * provider's raw completion is consumed token-by-token and forwarded to the
	 * given listener (so the UI can render the lesson while it is written),
	 * then the fully accumulated text is parsed into the validated plan.
	 */
	public LessonPlanResponse generateLessonStreaming(StudentProfileRequest profile,
			java.util.function.Consumer<String> onDelta) {
		if (apiKey == null || apiKey.isBlank()) {
			throw AiException.unavailable("AI lesson generation is not configured: the AI_API_KEY environment variable is not set.");
		}

		StringBuilder accumulated = new StringBuilder();
		chatClient.streamChatCompletion(baseUrl, apiKey.trim(), model, prepareMessages(profile), delta -> {
			accumulated.append(delta);
			onDelta.accept(delta);
		});

		return parseLesson(profile, accumulated.toString());
	}

	/** Builds the exact chat messages used for lesson generation. */
	public List<ChatMessage> prepareMessages(StudentProfileRequest profile) {
		String outputLanguage = languageName(profile.getLanguage());
		boolean hasMaterial = profile.getUploadedMaterial() != null && !profile.getUploadedMaterial().isBlank();
		return List.of(
				ChatMessage.system(buildSystemPrompt(outputLanguage, hasMaterial)),
				ChatMessage.user(buildUserPrompt(profile)));
	}

	/** System prompt: teacher persona, teaching rules, language + strict JSON schema. */
	private String buildSystemPrompt(String outputLanguage, boolean hasMaterial) {
		String materialGuidance = hasMaterial ? MATERIAL_GUIDANCE : "";
		return """
				You are the AI Teacher — a patient, expert tutor inside a learning application.

				Teach exactly the topic the student requested. Follow these rules:
				1. Adapt the depth and wording to the student's educational level.
				2. Design the lesson around the student's stated learning objective.
				3. Structure the lesson according to the requested teaching style.
				4. When prior knowledge is provided, build on it — do not re-explain what the student already knows.
				5. Match the requested depth when provided: "Quick" = essentials only, "Standard" = balanced, "Deep" = thorough.
				6. Fit the lesson to the available study time when provided: 10 minutes = quick intro + core concepts + one example;
				   20 minutes = focused lesson; 30-45 minutes = full treatment with examples, practice and revision.
				7. Start with a short, welcoming introduction to the topic.
				8. Break the topic into 3-5 logical sections and explain them progressively.
				9. Include a concrete example inside every section.
				10. Avoid unnecessary complexity; stay precise, accurate and clear.
				11. Keep standard technical terms accurate (you may add a short translation in parentheses).
				%s

				OUTPUT LANGUAGE: Write EVERY piece of returned text (titles, introduction, objectives,
				explanations, examples) in %s. Do not write in English unless %s is English.

				OUTPUT FORMAT: Respond with ONE valid JSON object only. No markdown fences, no commentary,
				no extra keys. Use exactly this schema:
				%s
				"learningObjectives" must contain 3-4 strings. "sections" must contain 3-5 objects,
				each with a non-empty "title", a detailed "explanation" and an "example".
				"visualHint" is optional and must be one of: equation, process, timeline, code, diagram —
				a short hint for a generic visual treatment; omit it when unsure.
				"estimatedMinutes" must be an integer between 5 and 60.
				""".formatted(materialGuidance, outputLanguage, outputLanguage, OUTPUT_SCHEMA);
	}

	/** User prompt carrying the student's actual submitted values, plus the
	 *  uploaded material when present. The topic line is omitted in material-only
	 *  mode; the material itself is the source. */
	private String buildUserPrompt(StudentProfileRequest profile) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Student Name: ").append(profile.getName()).append('\n');
		prompt.append("Educational Level: ").append(profile.getEducationLevel()).append('\n');
		prompt.append("Language: ").append(profile.getLanguage()).append('\n');
		prompt.append("Teaching Style: ").append(profile.getTeachingStyle()).append('\n');
		prompt.append("Learning Objective: ").append(profile.getObjective()).append('\n');
		if (profile.getTopic() != null && !profile.getTopic().isBlank()) {
			prompt.append("Topic: ").append(profile.getTopic()).append('\n');
		}
		if (profile.getPriorKnowledge() != null && !profile.getPriorKnowledge().isBlank()) {
			prompt.append("Existing Knowledge: ").append(profile.getPriorKnowledge()).append('\n');
		}
		if (profile.getAvailableTime() != null && !profile.getAvailableTime().isBlank()) {
			prompt.append("Available Study Time: ").append(profile.getAvailableTime()).append('\n');
		}
		if (profile.getDesiredDepth() != null && !profile.getDesiredDepth().isBlank()) {
			prompt.append("Desired Depth: ").append(profile.getDesiredDepth()).append('\n');
		}
		if (profile.getUploadedMaterial() != null && !profile.getUploadedMaterial().isBlank()) {
			prompt.append("\nUploaded Material (primary source):\n");
			String context = ragService.buildContext(profile.getUploadedMaterial(),
					retrievalQuery(profile), MAX_RETRIEVED_CHUNKS, MAX_MATERIAL_CHARS);
			if (context != null) {
				prompt.append(context);
			}
		}
		return prompt.toString().trim();
	}

	/**
	 * Compact query used to retrieve the most relevant excerpts of the uploaded
	 * material: the topic, objective, prior knowledge and level all contribute.
	 */
	private String retrievalQuery(StudentProfileRequest profile) {
		StringBuilder query = new StringBuilder();
		if (profile.getTopic() != null && !profile.getTopic().isBlank()) {
			query.append(profile.getTopic()).append(' ');
		}
		if (profile.getObjective() != null && !profile.getObjective().isBlank()) {
			query.append(profile.getObjective()).append(' ');
		}
		if (profile.getPriorKnowledge() != null && !profile.getPriorKnowledge().isBlank()) {
			query.append(profile.getPriorKnowledge()).append(' ');
		}
		if (profile.getEducationLevel() != null && !profile.getEducationLevel().isBlank()) {
			query.append(profile.getEducationLevel()).append(' ');
		}
		return query.toString().trim();
	}

	/**
	 * Parses and validates the provider's JSON content, then maps it onto a
	 * {@link LessonPlanResponse}. The profile fields are echoed back untouched;
	 * only the lesson content comes from the AI.
	 */
	private LessonPlanResponse parseLesson(StudentProfileRequest profile, String rawContent) {
		JsonNode root;
		try {
			root = json.readTree(stripJsonFences(rawContent));
		} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
			throw AiException.invalidResponse("AI returned content that is not valid JSON", ex);
		}
		if (root == null || !root.isObject()) {
			throw AiException.invalidResponse("AI returned content that is not a JSON object");
		}

		String lessonTitle = requiredText(root, "lessonTitle", "lessonTitle");
		String introduction = requiredText(root, "introduction", "introduction");
		int estimatedMinutes = parseEstimatedMinutes(root, profile.getEducationLevel());

		List<String> learningObjectives = new ArrayList<>();
		JsonNode objectives = root.path("learningObjectives");
		if (objectives.isArray()) {
			for (JsonNode node : objectives) {
				String objective = clean(node.asText(null));
				if (objective != null) {
					learningObjectives.add(objective);
				}
			}
		}
		if (learningObjectives.isEmpty()) {
			throw AiException.invalidResponse("AI response is missing learningObjectives");
		}

		List<LessonSection> sections = new ArrayList<>();
		JsonNode sectionsNode = root.path("sections");
		if (sectionsNode.isArray()) {
			for (JsonNode node : sectionsNode) {
				String title = clean(node.path("title").asText(null));
				String explanation = clean(node.path("explanation").asText(null));
				if (title == null || explanation == null) {
					continue; // malformed section entries are skipped, not trusted
				}
				String example = clean(node.path("example").asText(null));
				String description = explanation;
				if (example != null) {
					description = description + "\n\nExample: " + example;
				}
				sections.add(LessonSection.builder()
						.title(title)
						.description(description)
						.explanation(explanation)
						.example(example)
						.visualHint(clean(node.path("visualHint").asText(null)))
						.build());
			}
		}
		if (sections.isEmpty()) {
			throw AiException.invalidResponse("AI response is missing valid sections");
		}

		return LessonPlanResponse.builder()
				.studentName(profile.getName())
				.topic(profile.getTopic())
				.language(profile.getLanguage())
				.educationLevel(profile.getEducationLevel())
				.teachingStyle(profile.getTeachingStyle())
				.objective(profile.getObjective())
				.priorKnowledge(profile.getPriorKnowledge())
				.availableTime(profile.getAvailableTime())
				.desiredDepth(profile.getDesiredDepth())
				.lessonTitle(lessonTitle)
				.introduction(introduction)
				.learningObjectives(learningObjectives)
				.sections(sections)
				.estimatedMinutes(estimatedMinutes)
				.build();
	}

	private String requiredText(JsonNode root, String field, String label) {
		String value = clean(root.path(field).asText(null));
		if (value == null) {
			throw AiException.invalidResponse("AI response is missing required field: " + label);
		}
		return value;
	}

	/** Trusts the AI's estimate only when it is a plausible integer; otherwise falls back to a level-appropriate default. */
	private int parseEstimatedMinutes(JsonNode root, String educationLevel) {
		JsonNode node = root.path("estimatedMinutes");
		if (node.isIntegralNumber()) {
			int value = node.asInt();
			if (value >= MIN_ESTIMATED_MINUTES && value <= MAX_ESTIMATED_MINUTES) {
				return value;
			}
		}
		return defaultMinutesFor(educationLevel);
	}

	/** Deterministic fallback estimates, aligned with the student's education level. */
	static int defaultMinutesFor(String educationLevel) {
		String value = educationLevel == null ? "" : educationLevel.toLowerCase(Locale.ROOT);
		if (value.contains("professional")) {
			return 20;
		}
		if (value.contains("university")) {
			return 18;
		}
		if (value.contains("college")) {
			return 15;
		}
		if (value.contains("high school")) {
			return 12;
		}
		return 10;
	}

	/** Maps the student's language choice onto a name for the prompt (defaults to English). */
	private String languageName(String language) {
		if (language == null) {
			return "English";
		}
		String value = language.toLowerCase(Locale.ROOT);
		if (value.contains("hindi") || value.startsWith("hi")) {
			return "Hindi";
		}
		if (value.contains("kannada") || value.startsWith("kn")) {
			return "Kannada";
		}
		return "English";
	}

	/** Removes markdown fences / leading prose around the JSON payload if present. */
	private String stripJsonFences(String raw) {
		if (raw == null) {
			throw AiException.invalidResponse("AI returned no content");
		}
		String trimmed = raw.trim();
		int firstBrace = trimmed.indexOf('{');
		int lastBrace = trimmed.lastIndexOf('}');
		if (firstBrace >= 0 && lastBrace > firstBrace) {
			return trimmed.substring(firstBrace, lastBrace + 1);
		}
		return trimmed;
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
