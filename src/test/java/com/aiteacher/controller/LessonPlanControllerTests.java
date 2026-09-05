package com.aiteacher.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aiteacher.ai.AiException;
import com.aiteacher.dto.LessonPlanResponse;
import com.aiteacher.dto.StudentProfileRequest;
import com.aiteacher.service.AIService;

/**
 * Web-slice tests for {@link LessonPlanController}: validation, success
 * mapping, and the user-safe error mapping for AI failures. The AI service is
 * mocked, so these tests are deterministic and never touch the network.
 */
@WebMvcTest(LessonPlanController.class)
class LessonPlanControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AIService aiService;

	private static final String VALID_BODY = """
			{
			  "name": "Rahul",
			  "educationLevel": "College",
			  "language": "English",
			  "teachingStyle": "Visual",
			  "objective": "Understand the basics",
			  "topic": "Newton's Laws"
			}
			""";

	@Test
	void returnsGeneratedPlanForValidInput() throws Exception {
		LessonPlanResponse plan = LessonPlanResponse.builder()
				.studentName("Rahul")
				.topic("Newton's Laws")
				.language("English")
				.educationLevel("College")
				.teachingStyle("Visual")
				.objective("Understand the basics")
				.lessonTitle("Understanding Newton's Laws")
				.introduction("Welcome intro.")
				.learningObjectives(List.of("O1", "O2", "O3"))
				.sections(List.of(LessonPlanResponse.LessonSection.builder()
						.title("S1").description("D1").build()))
				.estimatedMinutes(15)
				.build();
		when(aiService.generateLesson(any(StudentProfileRequest.class))).thenReturn(plan);

		mockMvc.perform(post("/api/lesson/plan")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.studentName").value("Rahul"))
				.andExpect(jsonPath("$.topic").value("Newton's Laws"))
				.andExpect(jsonPath("$.lessonTitle").value("Understanding Newton's Laws"))
				.andExpect(jsonPath("$.learningObjectives.length()").value(3))
				.andExpect(jsonPath("$.sections.length()").value(1))
				.andExpect(jsonPath("$.estimatedMinutes").value(15));

		verify(aiService).generateLesson(any(StudentProfileRequest.class));
	}

	@Test
	void rejectsBlankTopicWith400() throws Exception {
		mockMvc.perform(post("/api/lesson/plan")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "R", "educationLevel": "School", "language": "English",
								  "teachingStyle": "Simple Explanation", "objective": "O", "topic": "   " }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("topic must not be empty"));

		verify(aiService, never()).generateLesson(any(StudentProfileRequest.class));
	}

	@Test
	void unconfiguredAiReturns503WithSafeMessage() throws Exception {
		when(aiService.generateLesson(any(StudentProfileRequest.class)))
				.thenThrow(AiException.unavailable("AI_API_KEY is not set"));

		mockMvc.perform(post("/api/lesson/plan")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value(
						"AI lesson generation is not configured yet. Set the AI_API_KEY environment variable and restart, then try again."));
	}

	@Test
	void upstreamAiFailureReturns502WithGenericMessage() throws Exception {
		when(aiService.generateLesson(any(StudentProfileRequest.class)))
				.thenThrow(AiException.upstream("HTTP 429 from provider"));

		mockMvc.perform(post("/api/lesson/plan")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("Unable to prepare the lesson. Please try again."));
	}

	@Test
	void materialOnlyModeIsAcceptedWithoutTopic() throws Exception {
		LessonPlanResponse plan = LessonPlanResponse.builder()
				.studentName("Rahul")
				.topic(null)
				.lessonTitle("From uploaded material")
				.introduction("I")
				.learningObjectives(List.of("O1"))
				.sections(List.of(LessonPlanResponse.LessonSection.builder()
						.title("S").description("D").build()))
				.estimatedMinutes(10)
				.build();
		when(aiService.generateLesson(any(StudentProfileRequest.class))).thenReturn(plan);

		mockMvc.perform(post("/api/lesson/plan")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "Meera", "educationLevel": "College", "language": "English",
								  "teachingStyle": "Visual", "objective": "Understand the basics",
								  "uploadedMaterial": "Actual extracted text from the uploaded file." }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lessonTitle").value("From uploaded material"));

		ArgumentCaptor<StudentProfileRequest> captor = ArgumentCaptor.forClass(StudentProfileRequest.class);
		verify(aiService).generateLesson(captor.capture());
		assertEquals("Actual extracted text from the uploaded file.", captor.getValue().getUploadedMaterial());
	}

	@Test
	void invalidAiResponseReturns502WithGenericMessage() throws Exception {
		when(aiService.generateLesson(any(StudentProfileRequest.class)))
				.thenThrow(AiException.invalidResponse("missing sections"));

		mockMvc.perform(post("/api/lesson/plan")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("Unable to prepare the lesson. Please try again."));
	}
}
