package com.aiteacher.ai;

import java.util.List;

/**
 * Thin transport contract for talking to an OpenAI-compatible chat-completions
 * endpoint ({@code POST {baseUrl}/chat/completions}). Keeping the provider call
 * behind this interface means the AI provider can be swapped (OpenAI, Groq,
 * OpenRouter, DeepSeek, Ollama, ...) without touching the lesson generation
 * logic.
 */
public interface AiChatClient {

	/**
	 * Sends a chat-completion request and returns the assistant's message content.
	 *
	 * @param baseUrl  provider base URL, e.g. {@code https://api.openai.com/v1}
	 * @param apiKey   bearer token; may be blank for local providers without auth
	 * @param model    model identifier
	 * @param messages ordered conversation messages
	 * @return the raw assistant message content (expected to be JSON text)
	 * @throws AiException with kind {@code UPSTREAM} on any call failure
	 */
	String chatCompletion(String baseUrl, String apiKey, String model, List<ChatMessage> messages);

	/** One chat message with a role ({@code system}, {@code user}, ...). */
	record ChatMessage(String role, String content) {

		public static ChatMessage system(String content) {
			return new ChatMessage("system", content);
		}

		public static ChatMessage user(String content) {
			return new ChatMessage("user", content);
		}
	}
}
