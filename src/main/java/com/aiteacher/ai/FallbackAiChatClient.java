package com.aiteacher.ai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Resilient {@link AiChatClient}: tries the primary provider first and, when it
 * fails (network error, timeout, HTTP error, rate limit), retries the same
 * request against a fallback provider before giving up.
 *
 * <p>The fallback is configured through environment variables; when they are
 * absent the wrapper is transparent and simply forwards to the delegate:</p>
 *
 * <pre>
 *   AI_FALLBACK_BASE_URL — e.g. https://openrouter.ai/api/v1  (empty = disabled)
 *   AI_FALLBACK_API_KEY  — the fallback provider's bearer token
 *   AI_FALLBACK_MODEL    — e.g. meta-llama/llama-3.1-8b-instruct
 * </pre>
 *
 * <p>Because {@link OpenAiCompatibleChatClient} takes the provider triple as
 * per-call parameters, one delegate instance serves both providers — the
 * wrapper only swaps the credentials on retry. Marked {@link Primary @Primary}
 * so every AI service gets resilience without any change to their code.</p>
 */
@Component
@Primary
public class FallbackAiChatClient implements AiChatClient {

	private final AiChatClient delegate;
	private final String fallbackBaseUrl;
	private final String fallbackApiKey;
	private final String fallbackModel;

	public FallbackAiChatClient(AiChatClient delegate,
			@Value("${AI_FALLBACK_BASE_URL:}") String fallbackBaseUrl,
			@Value("${AI_FALLBACK_API_KEY:}") String fallbackApiKey,
			@Value("${AI_FALLBACK_MODEL:}") String fallbackModel) {
		this.delegate = delegate;
		this.fallbackBaseUrl = fallbackBaseUrl == null ? "" : fallbackBaseUrl.trim();
		this.fallbackApiKey = fallbackApiKey == null ? "" : fallbackApiKey.trim();
		this.fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
	}

	/** @return true when a usable fallback provider is configured. */
	public boolean isFallbackConfigured() {
		return !fallbackBaseUrl.isEmpty() && !fallbackApiKey.isEmpty() && !fallbackModel.isEmpty();
	}

	@Override
	public String chatCompletion(String baseUrl, String apiKey, String model, List<ChatMessage> messages) {
		try {
			return delegate.chatCompletion(baseUrl, apiKey, model, messages);
		} catch (AiException primaryFailure) {
			if (!isFallbackConfigured()) {
				throw primaryFailure;
			}
			return delegate.chatCompletion(fallbackBaseUrl, fallbackApiKey, fallbackModel, messages);
		}
	}

	@Override
	public void streamChatCompletion(String baseUrl, String apiKey, String model,
			List<ChatMessage> messages, TokenListener listener) {
		boolean[] emitted = { false };
		TokenListener guardedListener = delta -> {
			emitted[0] = true;
			listener.onDelta(delta);
		};
		try {
			delegate.streamChatCompletion(baseUrl, apiKey, model, messages, guardedListener);
		} catch (AiException primaryFailure) {
			if (!isFallbackConfigured()) {
				throw primaryFailure;
			}
			if (emitted[0]) {
				// Deltas already reached the caller; a retry would replay them.
				throw primaryFailure;
			}
			delegate.streamChatCompletion(fallbackBaseUrl, fallbackApiKey, fallbackModel, messages, listener);
		}
	}
}
