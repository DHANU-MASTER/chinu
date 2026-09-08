package com.aiteacher.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FallbackAiChatClient}: primary success, fallback
 * activation, transparency without configuration, and the no-replay guard for
 * partially streamed responses.
 */
class FallbackAiChatClientTests {

	private AiChatClient delegate;
	private FallbackAiChatClient configured;
	private FallbackAiChatClient unconfigured;

	@BeforeEach
	void setUp() {
		delegate = mock(AiChatClient.class);
		unconfigured = new FallbackAiChatClient(delegate, "", "", "");
		configured = new FallbackAiChatClient(delegate,
				"https://fallback.example.com/v1", "fallback-key", "fallback-model");
	}

	@Test
	void primarySuccessSkipsTheFallback() {
		when(delegate.chatCompletion(anyString(), anyString(), anyString(), anyList()))
				.thenReturn("{\"ok\":true}");

		String result = configured.chatCompletion("https://primary/v1", "key", "model", List.of());

		assertEquals("{\"ok\":true}", result);
		verify(delegate).chatCompletion("https://primary/v1", "key", "model", List.of());
		verify(delegate, never()).chatCompletion(eq("https://fallback.example.com/v1"), anyString(), anyString(), anyList());
	}

	@Test
	void primaryFailureRetriesAgainstTheFallbackProvider() {
		when(delegate.chatCompletion(anyString(), anyString(), anyString(), anyList()))
				.thenThrow(AiException.upstream("primary down"))
				.thenReturn("{\"from\":\"fallback\"}");

		String result = configured.chatCompletion("https://primary/v1", "key", "model", List.of());

		assertEquals("{\"from\":\"fallback\"}", result);
		verify(delegate).chatCompletion("https://fallback.example.com/v1", "fallback-key", "fallback-model", List.of());
	}

	@Test
	void fallbackFailureSurfacesTheFallbackError() {
		when(delegate.chatCompletion(anyString(), anyString(), anyString(), anyList()))
				.thenThrow(AiException.upstream("primary down"))
				.thenThrow(AiException.upstream("fallback down"));

		AiException thrown = assertThrows(AiException.class,
				() -> configured.chatCompletion("https://primary/v1", "key", "model", List.of()));

		assertEquals("fallback down", thrown.getMessage());
	}

	@Test
	void withoutFallbackConfiguredThePrimaryErrorIsRethrownUnchanged() {
		AiException primary = AiException.unavailable("no key");
		when(delegate.chatCompletion(anyString(), anyString(), anyString(), anyList())).thenThrow(primary);

		AiException thrown = assertThrows(AiException.class,
				() -> unconfigured.chatCompletion("https://primary/v1", "key", "model", List.of()));

		assertSame(primary, thrown, "the exact primary exception should propagate");
	}

	@Test
	void streamingFallsBackWhenNothingWasEmitted() {
		doThrow(AiException.upstream("primary down"))
				.doNothing()
				.when(delegate)
				.streamChatCompletion(anyString(), anyString(), anyString(), anyList(), any());

		StringBuilder received = new StringBuilder();
		configured.streamChatCompletion("https://primary/v1", "key", "model", List.of(),
				received::append);

		verify(delegate).streamChatCompletion(eq("https://fallback.example.com/v1"), eq("fallback-key"),
				eq("fallback-model"), anyList(), any());
	}

	@Test
	void streamingNeverReplaysDeltasAfterPartialOutput() {
		AiChatClient failingAfterDeltas = mock(AiChatClient.class);
		// First call (primary): emit a delta, then fail
		doAnswer(invocation -> {
			AiChatClient.TokenListener listener = invocation.getArgument(4);
			listener.onDelta("partial ");
			throw AiException.upstream("died mid-stream");
		}).when(failingAfterDeltas)
				.streamChatCompletion(anyString(), anyString(), anyString(), anyList(), any());
		FallbackAiChatClient client = new FallbackAiChatClient(failingAfterDeltas,
				"https://fallback.example.com/v1", "fallback-key", "fallback-model");

		AiException thrown = assertThrows(AiException.class,
				() -> client.streamChatCompletion("https://primary/v1", "key", "model", List.of(), delta -> { }));

		assertEquals("died mid-stream", thrown.getMessage());
		verify(failingAfterDeltas, never()).streamChatCompletion(
				eq("https://fallback.example.com/v1"), anyString(), anyString(), anyList(), any());
	}

	@Test
	void isFallbackConfiguredReflectsAllThreeProperties() {
		assertFalse(unconfigured.isFallbackConfigured());
		assertTrue(configured.isFallbackConfigured());
		// Missing any one of the three properties disables the fallback
		assertFalse(new FallbackAiChatClient(delegate, "https://x/v1", "key", "").isFallbackConfigured());
	}
}
