package com.aiteacher.ai;

/**
 * Failure type raised by the AI lesson-generation pipeline.
 *
 * <p>The {@link Kind} lets the controller map failures to the right HTTP status:
 * <ul>
 *   <li>{@link Kind#UNAVAILABLE} — the AI provider is not configured (missing API key).</li>
 *   <li>{@link Kind#UPSTREAM} — the AI provider call itself failed (network, timeout, HTTP error).</li>
 *   <li>{@link Kind#INVALID_RESPONSE} — the provider answered but with malformed/unusable content.</li>
 * </ul>
 *
 * The {@code detail} field carries implementation details for server-side logs only;
 * it must never be sent to the browser.
 */
public class AiException extends RuntimeException {

	public enum Kind {
		/** AI provider not configured (e.g. missing API key). Maps to HTTP 503. */
		UNAVAILABLE,
		/** The AI call failed upstream (network/timeout/HTTP status). Maps to HTTP 502. */
		UPSTREAM,
		/** The AI response could not be validated into a lesson plan. Maps to HTTP 502. */
		INVALID_RESPONSE
	}

	private final Kind kind;

	private AiException(Kind kind, String detail) {
		super(detail);
		this.kind = kind;
	}

	private AiException(Kind kind, String detail, Throwable cause) {
		super(detail, cause);
		this.kind = kind;
	}

	public static AiException unavailable(String detail) {
		return new AiException(Kind.UNAVAILABLE, detail);
	}

	public static AiException upstream(String detail) {
		return new AiException(Kind.UPSTREAM, detail);
	}

	public static AiException upstream(String detail, Throwable cause) {
		return new AiException(Kind.UPSTREAM, detail, cause);
	}

	public static AiException invalidResponse(String detail) {
		return new AiException(Kind.INVALID_RESPONSE, detail);
	}

	public static AiException invalidResponse(String detail, Throwable cause) {
		return new AiException(Kind.INVALID_RESPONSE, detail, cause);
	}

	public Kind getKind() {
		return kind;
	}

	public boolean isUnavailable() {
		return kind == Kind.UNAVAILABLE;
	}
}
