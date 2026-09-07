package com.aiteacher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aiteacher.ai.AiChatClient;
import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.aiteacher.ai.AiException;	import com.aiteacher.dto.LessonPlanResponse;
	import com.aiteacher.dto.StudentProfileRequest;
	import com.aiteacher.rag.RAGService;
	import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AILessonService}. The HTTP provider is replaced by a
 * fake {@link AiChatClient}, so the tests cover prompt construction from real
 * user input, response validation/mapping and error handling — no network, no
 * API key required.
 */
class AILessonServiceTests {

	private FakeChatClient chatClient;
	private ObjectMapper json;

	@BeforeEach
	void setUp() {
		this.chatClient = new FakeChatClient();
		this.json = new ObjectMapper();
	}

	private AILessonService service(String apiKey) {
		return new AILessonService(chatClient, json, new RAGService(), apiKey, "https://api.openai.com/v1", "gpt-4o-mini");
	}

	private StudentProfileRequest profile(String name, String level, String language,
			String style, String objective, String topic) {
		return profile(name, level, language, style, objective, topic, null);
	}

	private StudentProfileRequest profile(String name, String level, String language,
			String style, String objective, String topic, String material) {
		return StudentProfileRequest.builder()
				.name(name)
				.educationLevel(level)
				.language(language)
				.teachingStyle(style)
				.objective(objective)
				.topic(topic)
				.uploadedMaterial(material)
				.build();
	}

	private static final String VALID_AI_JSON = """
			{
			  "lessonTitle": "Understanding the topic",
			  "introduction": "A welcoming introduction.",
			  "learningObjectives": ["Objective one", "Objective two", "Objective three"],
			  "sections": [
			    { "title": "Section 1", "explanation": "Detailed explanation one.", "example": "Worked example one." },
			    { "title": "Section 2", "explanation": "Detailed explanation two.", "example": "Worked example two." },
			    { "title": "Section 3", "explanation": "Detailed explanation three.", "example": "" }
			  ],
			  "estimatedMinutes": 15
			}
			""";

	@Test
	void generatesPlanFromActualUserInput() {
		StudentProfileRequest input = profile("Meera", "Professional", "English", "Visual",
				"Understand the basics", "Karnataka temple architecture");
		chatClient.respond(VALID_AI_JSON);

		LessonPlanResponse plan = service("sk-test-123").generateLesson(input);

		// Profile values are echoed back verbatim.
		assertEquals("Meera", plan.getStudentName());
		assertEquals("Karnataka temple architecture", plan.getTopic());
		assertEquals("English", plan.getLanguage());
		assertEquals("Professional", plan.getEducationLevel());
		assertEquals("Visual", plan.getTeachingStyle());
		assertEquals("Understand the basics", plan.getObjective());

		// AI content is mapped.
		assertEquals("Understanding the topic", plan.getLessonTitle());
		assertEquals("A welcoming introduction.", plan.getIntroduction());
		assertEquals(3, plan.getLearningObjectives().size());
		assertEquals(3, plan.getSections().size());
		assertEquals(15, plan.getEstimatedMinutes());

		// Section without example has no "Example:" suffix.
		assertTrue(plan.getSections().get(2).getDescription().startsWith("Detailed explanation three."));
	}

	@Test
	void sectionFieldsMapIndividuallyIncludingVisualHint() {
		chatClient.respond("""
				{ "lessonTitle": "T", "introduction": "I", "learningObjectives": ["O1"],
				  "sections": [
				    { "title": "Equation section", "explanation": "The math behind it.", "example": "2x = 8", "visualHint": "equation" },
				    { "title": "Plain section", "explanation": "Just an explanation." }
				  ],
				  "estimatedMinutes": 12 }
				""");

		LessonPlanResponse plan = service("sk-test-123")
				.generateLesson(profile("A", "School", "English", "Simple Explanation", "O", "T"));

		LessonPlanResponse.LessonSection first = plan.getSections().get(0);
		assertEquals("Equation section", first.getTitle());
		assertEquals("The math behind it.", first.getExplanation());
		assertEquals("2x = 8", first.getExample());
		assertEquals("equation", first.getVisualHint());

		LessonPlanResponse.LessonSection second = plan.getSections().get(1);
		assertEquals("Just an explanation.", second.getExplanation());
		assertNull(second.getExample());
		assertNull(second.getVisualHint());
	}

	@Test
	void sectionDescriptionIncludesExampleWhenProvided() {
		chatClient.respond(VALID_AI_JSON);
		LessonPlanResponse plan = service("sk-test-123")
				.generateLesson(profile("Meera", "College", "English", "Example-Based",
						"Prepare for my exam", "Photosynthesis"));
		assertEquals("Detailed explanation one.\n\nExample: Worked example one.",
				plan.getSections().get(0).getDescription());
	}

	@Test
	void userPromptCarriesEveryActualField() {
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("Rahul", "College", "English", "Visual",
				"Understand the basics", "Newton's Laws"));

		ChatMessage userMessage = chatClient.lastUserMessage();
		assertNotNull(userMessage);
		String prompt = userMessage.content();
		assertTrue(prompt.contains("Student Name: Rahul"));
		assertTrue(prompt.contains("Educational Level: College"));
		assertTrue(prompt.contains("Language: English"));
		assertTrue(prompt.contains("Teaching Style: Visual"));
		assertTrue(prompt.contains("Learning Objective: Understand the basics"));
		assertTrue(prompt.contains("Topic: Newton's Laws"));

		ChatMessage systemMessage = chatClient.lastSystemMessage();
		assertTrue(systemMessage.content().contains("explanations, examples) in English"));
		assertTrue(systemMessage.content().contains("estimatedMinutes"));
	}

	@Test
	void personalizationFieldsReachTheAiPromptAndEchoBack() {
		chatClient.respond(VALID_AI_JSON);
		StudentProfileRequest input = StudentProfileRequest.builder()
				.name("Divya")
				.educationLevel("College")
				.language("English")
				.teachingStyle("Visual")
				.objective("Prepare for my exam")
				.topic("Quantum Computing")
				.priorKnowledge("I know bits and Boolean logic")
				.availableTime("10 minutes")
				.desiredDepth("Quick")
				.build();

		LessonPlanResponse plan = service("sk-test-123").generateLesson(input);

		String prompt = chatClient.lastUserMessage().content();
		assertTrue(prompt.contains("Existing Knowledge: I know bits and Boolean logic"));
		assertTrue(prompt.contains("Available Study Time: 10 minutes"));
		assertTrue(prompt.contains("Desired Depth: Quick"));

		// System prompt must instruct the AI to actually use these values.
		String system = chatClient.lastSystemMessage().content();
		assertTrue(system.contains("do not re-explain what the student already knows"));
		assertTrue(system.contains("Quick"));
		assertTrue(system.contains("Fit the lesson to the available study time"));

		// The response echoes them back for the teaching screen.
		assertEquals("I know bits and Boolean logic", plan.getPriorKnowledge());
		assertEquals("10 minutes", plan.getAvailableTime());
		assertEquals("Quick", plan.getDesiredDepth());
	}

	@Test
	void blankPersonalizationFieldsAreOmittedFromPrompt() {
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("Rahul", "College", "English", "Visual",
				"Understand the basics", "Newton's Laws"));

		String prompt = chatClient.lastUserMessage().content();
		assertFalse(prompt.contains("Existing Knowledge:"));
		assertFalse(prompt.contains("Available Study Time:"));
		assertFalse(prompt.contains("Desired Depth:"));
	}

	@Test
	void promptRequestsHindiAndCarriesHindiTopic() {
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("Priya", "School", "Hindi", "Example-Based",
				"Prepare for my exam", "Photosynthesis"));

		assertTrue(chatClient.lastUserMessage().content().contains("Topic: Photosynthesis"));
		assertTrue(chatClient.lastSystemMessage().content().contains("in Hindi"));
		assertTrue(chatClient.lastUserMessage().content().contains("Language: Hindi"));
	}

	@Test
	void promptRequestsKannada() {
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("Arjun", "Professional", "Kannada", "Step-by-Step",
				"Learn practical applications", "Java Exception Handling"));

		assertTrue(chatClient.lastSystemMessage().content().contains("in Kannada"));
		assertTrue(chatClient.lastUserMessage().content().contains("Language: Kannada"));
	}

	@Test
	void endpointAndModelArePassedToClient() {
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("A", "School", "English", "Simple Explanation",
				"O", "T"));
		assertEquals("https://api.openai.com/v1", chatClient.lastBaseUrl);
		assertEquals("sk-test-123", chatClient.lastApiKey);
		assertEquals("gpt-4o-mini", chatClient.lastModel);
	}

	@Test
	void missingApiKeyFailsAsUnavailableWithoutCallingProvider() {
		AiException ex = assertThrows(AiException.class,
				() -> service("   ").generateLesson(profile("A", "School", "English",
						"Simple Explanation", "O", "T")));
		assertTrue(ex.isUnavailable());
		assertTrue(ex.getMessage().contains("AI_API_KEY"));
		assertEquals(0, chatClient.callCount);
	}

	@Test
	void upstreamProviderFailurePropagates() {
		chatClient.failWith(AiException.upstream("simulated HTTP 429"));
		AiException ex = assertThrows(AiException.class,
				() -> service("sk-test-123").generateLesson(profile("A", "School", "English",
						"Simple Explanation", "O", "T")));
		assertEquals(AiException.Kind.UPSTREAM, ex.getKind());
	}

	@Test
	void nonJsonAiContentIsRejected() {
		chatClient.respond("Sorry, I cannot help with that.");
		AiException ex = assertThrows(AiException.class,
				() -> service("sk-test-123").generateLesson(profile("A", "School", "English",
						"Simple Explanation", "O", "T")));
		assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
	}

	@Test
	void missingLearningObjectivesAreRejected() {
		chatClient.respond("""
				{ "lessonTitle": "T", "introduction": "I", "learningObjectives": [],
				  "sections": [ { "title": "S", "explanation": "E", "example": "X" } ], "estimatedMinutes": 10 }
				""");
		AiException ex = assertThrows(AiException.class,
				() -> service("sk-test-123").generateLesson(profile("A", "School", "English",
						"Simple Explanation", "O", "T")));
		assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
		assertTrue(ex.getMessage().contains("learningObjectives"));
	}

	@Test
	void missingSectionsAreRejected() {
		chatClient.respond("""
				{ "lessonTitle": "T", "introduction": "I",
				  "learningObjectives": ["O1"], "sections": [], "estimatedMinutes": 10 }
				""");
		AiException ex = assertThrows(AiException.class,
				() -> service("sk-test-123").generateLesson(profile("A", "School", "English",
						"Simple Explanation", "O", "T")));
		assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
	}

	@Test
	void missingLessonTitleIsRejected() {
		chatClient.respond("""
				{ "introduction": "I", "learningObjectives": ["O1"],
				  "sections": [ { "title": "S", "explanation": "E" } ], "estimatedMinutes": 10 }
				""");
		AiException ex = assertThrows(AiException.class,
				() -> service("sk-test-123").generateLesson(profile("A", "School", "English",
						"Simple Explanation", "O", "T")));
		assertEquals(AiException.Kind.INVALID_RESPONSE, ex.getKind());
	}

	@Test
	void markdownFencedAiJsonIsStillParsed() {
		chatClient.respond("```json\n" + VALID_AI_JSON + "\n```");
		LessonPlanResponse plan = service("sk-test-123")
				.generateLesson(profile("A", "School", "English", "Simple Explanation", "O", "T"));
		assertEquals("Understanding the topic", plan.getLessonTitle());
		assertEquals(15, plan.getEstimatedMinutes());
	}

	@Test
	void materialOnlyModeSendsMaterialAsPrimarySourceWithoutTopic() {
		String material = "Cirrus clouds form above 5,000 metres and are made of ice crystals.";
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("Meera", "School", "English", "Simple Explanation",
				"Understand the basics", null, material));

		String prompt = chatClient.lastUserMessage().content();
		assertTrue(prompt.contains("Uploaded Material (primary source):"));
		assertTrue(prompt.contains(material));
		assertFalse(prompt.contains("Topic:"));

		String system = chatClient.lastSystemMessage().content();
		assertTrue(system.contains("Use the supplied learning material as the primary source."));
		assertTrue(system.contains("Do not invent facts that contradict the supplied material."));
		assertTrue(system.contains("clearly state that additional information is required"));
	}

	@Test
	void topicAndMaterialAreBothSentToAi() {
		String material = "Photosynthesis takes place in the chloroplasts of plant cells.";
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("Priya", "High School", "English", "Example-Based",
				"Prepare for my exam", "Photosynthesis", material));

		String prompt = chatClient.lastUserMessage().content();
		assertTrue(prompt.contains("Topic: Photosynthesis"));
		assertTrue(prompt.contains("Uploaded Material (primary source):"));
		assertTrue(prompt.contains(material));
	}

	@Test
	void materialGoesThroughRetrievalInsteadOfBlindTruncation() {
		StringBuilder relevant = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			relevant.append("chlorophyll absorbs light energy inside the chloroplast membranes. ");
		}
		StringBuilder filler = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			filler.append("unrelated filler about railway timetables and weather stations. ");
		}
		String material = "QUERY_TARGET_MARKER " + relevant + filler + " TAIL_MARKER_NOT_SUPPOSED_TO_REACH_THE_AI";
		chatClient.respond(VALID_AI_JSON);

		service("sk-test-123").generateLesson(profile("A", "College", "English", "Visual",
				"understand how chlorophyll captures light", "Photosynthesis", material));

		String prompt = chatClient.lastUserMessage().content();
		assertTrue(prompt.contains("Uploaded Material (primary source):"));
		assertTrue(prompt.contains("[Excerpt 1]"));
		// The retrieved context is bounded and matches the query, not the raw document.
		assertTrue(prompt.contains("chlorophyll absorbs light energy"));
		assertTrue(prompt.contains("QUERY_TARGET_MARKER"));
		assertFalse(prompt.contains("TAIL_MARKER_NOT_SUPPOSED_TO_REACH_THE_AI"));
		assertTrue(prompt.length() < material.length());
	}

	@Test
	void noMaterialMeansNoGroundingInstruction() {
		chatClient.respond(VALID_AI_JSON);
		service("sk-test-123").generateLesson(profile("A", "School", "English", "Simple Explanation",
				"O", "T"));

		String system = chatClient.lastSystemMessage().content();
		assertFalse(system.contains("Use the supplied learning material as the primary source."));
	}

	@Test
	void outOfRangeEstimatedMinutesFallsBackToLevelDefault() {
		chatClient.respond("""
				{ "lessonTitle": "T", "introduction": "I", "learningObjectives": ["O1"],
				  "sections": [ { "title": "S", "explanation": "E", "example": "X" } ],
				  "estimatedMinutes": 9999 }
				""");
		LessonPlanResponse plan = service("sk-test-123")
				.generateLesson(profile("A", "Professional", "English", "Simple Explanation", "O", "T"));
		assertEquals(AILessonService.defaultMinutesFor("Professional"), plan.getEstimatedMinutes());

		// Also when the AI omits the field entirely.
		chatClient.respond("""
				{ "lessonTitle": "T", "introduction": "I", "learningObjectives": ["O1"],
				  "sections": [ { "title": "S", "explanation": "E", "example": "X" } ] }
				""");
		LessonPlanResponse schoolPlan = service("sk-test-123")
				.generateLesson(profile("A", "School", "English", "Simple Explanation", "O", "T"));
		assertEquals(AILessonService.defaultMinutesFor("School"), schoolPlan.getEstimatedMinutes());
	}

	/** Fake transport: returns a canned body or throws, and records the request. */
	@Test
	void streamingGenerationForwardsDeltasAndParsesSamePlan() {
		StudentProfileRequest input = profile("Meera", "Professional", "English", "Visual",
				"Understand the basics", "Karnataka temple architecture");
		chatClient.respond(VALID_AI_JSON);

		StringBuilder receivedDeltas = new StringBuilder();
		LessonPlanResponse plan = service("sk-test-123").generateLessonStreaming(input, receivedDeltas::append);

		assertEquals(VALID_AI_JSON, receivedDeltas.toString());
		assertEquals("Understanding the topic", plan.getLessonTitle());
	}

	private static final class FakeChatClient implements AiChatClient {

		private String responseBody;
		private AiException failure;
		private String lastBaseUrl;
		private String lastApiKey;
		private String lastModel;
		private List<ChatMessage> lastMessages;
		private int callCount;

		void respond(String body) {
			this.responseBody = body;
			this.failure = null;
		}

		void failWith(AiException ex) {
			this.failure = ex;
			this.responseBody = null;
		}

		ChatMessage lastSystemMessage() {
			return lastMessages.stream().filter(m -> m.role().equals("system")).findFirst().orElse(null);
		}

		ChatMessage lastUserMessage() {
			return lastMessages.stream().filter(m -> m.role().equals("user")).findFirst().orElse(null);
		}

		@Override
		public String chatCompletion(String baseUrl, String apiKey, String model, List<ChatMessage> messages) {
			this.callCount++;
			this.lastBaseUrl = baseUrl;
			this.lastApiKey = apiKey;
			this.lastModel = model;
			this.lastMessages = messages;
			if (failure != null) {
				throw failure;
			}
			return responseBody;
		}

		@Override
		public void streamChatCompletion(String baseUrl, String apiKey, String model,
				List<ChatMessage> messages, TokenListener listener) {
			this.lastBaseUrl = baseUrl;
			this.lastApiKey = apiKey;
			this.lastModel = model;
			this.lastMessages = messages;
			if (failure != null) {
				throw failure;
			}
			// Emit the response body in three chunks, like a real SSE provider would.
			int third = Math.max(1, responseBody.length() / 3);
			listener.onDelta(responseBody.substring(0, third));
			listener.onDelta(responseBody.substring(third, 2 * third));
			listener.onDelta(responseBody.substring(2 * third));
		}
	}
}
