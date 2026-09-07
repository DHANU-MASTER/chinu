package com.aiteacher.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.aiteacher.ai.AiChatClient.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default {@link AiChatClient} implementation: a RestClient call against any
 * OpenAI-compatible {@code /chat/completions} endpoint. Configuration arrives
 * exclusively from environment variables (never hardcoded):
 *
 * <pre>
 *   AI_API_KEY    — bearer token            (default: empty)
 *   AI_BASE_URL   — API base URL            (default: https://api.openai.com/v1)
 *   AI_MODEL      — model identifier        (default: gpt-4o-mini)
 *   AI_TIMEOUT_SECONDS — read timeout       (default: 90)
 * </pre>
 */
@Component
public class OpenAiCompatibleChatClient implements AiChatClient {

	private final ObjectMapper json;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;

	public OpenAiCompatibleChatClient(ObjectMapper json,
			@Value("${AI_TIMEOUT_SECONDS:90}") int readTimeoutSeconds) {
		this.json = json;
		this.connectTimeoutMs = 10_000;
		this.readTimeoutMs = Math.max(1, readTimeoutSeconds) * 1_000;
	}

	@Override
	public String chatCompletion(String baseUrl, String apiKey, String model, List<ChatMessage> messages) {
		String endpoint = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com/v1" : baseUrl;
		if (endpoint.endsWith("/")) {
			endpoint = endpoint.substring(0, endpoint.length() - 1);
		}

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeoutMs);
		requestFactory.setReadTimeout(readTimeoutMs);

		RestClient client = RestClient.builder()
				.requestFactory(requestFactory)
				.build();

		Map<String, Object> payload = Map.of(
				"model", model,
				"temperature", 0.4,
				"response_format", Map.of("type", "json_object"),
				"messages", messages.stream()
						.map(message -> Map.of("role", message.role(), "content", message.content()))
						.toList());

		String responseBody;
		try {
			responseBody = client.post()
					.uri(URI.create(endpoint + "/chat/completions"))
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(String.class);
		} catch (AiException ex) {
			throw ex;
		} catch (RestClientResponseException ex) {
			throw AiException.upstream("AI provider returned HTTP " + ex.getStatusCode().value()
					+ ": " + safeSnippet(ex.getResponseBodyAsString()), ex);
		} catch (ResourceAccessException ex) {
			throw AiException.upstream("AI provider unreachable or timed out: " + ex.getMessage(), ex);
		} catch (RuntimeException ex) {
			throw AiException.upstream("AI request failed: " + ex.getMessage(), ex);
		}

		return extractAssistantContent(responseBody);
	}

	@Override
	public void streamChatCompletion(String baseUrl, String apiKey, String model,
			List<ChatMessage> messages, TokenListener listener) {
		String endpoint = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com/v1" : baseUrl;
		if (endpoint.endsWith("/")) {
			endpoint = endpoint.substring(0, endpoint.length() - 1);
		}

		Map<String, Object> payload = Map.of(
				"model", model,
				"temperature", 0.4,
				"stream", true,
				"messages", messages.stream()
						.map(message -> Map.of("role", message.role(), "content", message.content()))
						.toList());

		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) URI.create(endpoint + "/chat/completions").toURL().openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Authorization", "Bearer " + apiKey);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Accept", "text/event-stream");
			connection.setConnectTimeout(connectTimeoutMs);
			connection.setReadTimeout(readTimeoutMs);
			connection.setDoOutput(true);
			connection.getOutputStream().write(json.writeValueAsBytes(payload));

			int status = connection.getResponseCode();
			if (status < 200 || status >= 300) {
				throw AiException.upstream("AI provider returned HTTP " + status + " during streaming");
			}

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (!line.startsWith("data:")) {
						continue;
					}
					String data = line.substring(5).trim();
					if (data.isEmpty()) {
						continue;
					}
					if ("[DONE]".equals(data)) {
						break;
					}
					String delta = json.readTree(data)
							.path("choices").path(0).path("delta").path("content").asText("");
					if (!delta.isEmpty()) {
						listener.onDelta(delta);
					}
				}
			}
		} catch (AiException ex) {
			throw ex;
		} catch (IOException ex) {
			throw AiException.upstream("AI streaming call failed: " + ex.getMessage(), ex);
		} catch (Exception ex) {
			throw AiException.upstream("AI streaming request failed: " + ex.getMessage(), ex);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/** Pulls {@code choices[0].message.content} out of the provider response. */
	private String extractAssistantContent(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			throw AiException.upstream("AI provider returned an empty response body");
		}
		try {
			JsonNode root = json.readTree(responseBody);
			JsonNode error = root.path("error");
			if (!error.isMissingNode() && !error.isNull()) {
				throw AiException.upstream("AI provider error: " + error.path("message").asText("unknown error"));
			}
			String content = root.path("choices").path(0).path("message").path("content").asText(null);
			if (content == null || content.isBlank()) {
				throw AiException.upstream("AI provider response contained no assistant message content");
			}
			return content;
		} catch (AiException ex) {
			throw ex;
		} catch (Exception ex) { // checked JsonProcessingException or any other failure
			throw AiException.upstream("Could not read AI provider response: " + ex.getMessage(), ex);
		}
	}

	private static String safeSnippet(String body) {
		if (body == null) {
			return null;
		}
		String cleaned = body.replace('\n', ' ').trim();
		return cleaned.length() > 200 ? cleaned.substring(0, 200) + "…" : cleaned;
	}
}
