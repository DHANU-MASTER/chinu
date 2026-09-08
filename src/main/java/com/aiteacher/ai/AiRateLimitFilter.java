package com.aiteacher.ai;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-user sliding-window rate limiter for the AI endpoints — dependency-free.
 *
 * <p>Every AI call costs real money and latency, so abusive or runaway clients
 * must be shed before they reach the provider. The limiter:</p>
 * <ul>
 *   <li>keys on the {@code X-Student-Email} header the frontend sends
 *       (falling back to the client IP for anonymous/demo traffic);</li>
 *   <li>enforces {@value #DEFAULT_LIMIT} requests per {@value #DEFAULT_WINDOW_SECONDS}s
 *       (configurable via {@code AI_RATE_LIMIT} / {@code AI_RATE_WINDOW_SECONDS});</li>
 *   <li>only guards the AI-costing paths ({@code /api/lesson/**}, {@code /api/assessment/**});</li>
 *   <li>answers excess requests with {@code 429 Too Many Requests} plus a
 *       JSON body and a {@code Retry-After} header.</li>
 * </ul>
 *
 * <p>Buckets are cleaned lazily; a small periodic sweep bounds memory when many
 * distinct identities appear.</p>
 */
@Component
@Order(2)
public class AiRateLimitFilter extends OncePerRequestFilter {

	static final int DEFAULT_LIMIT = 30;
	static final int DEFAULT_WINDOW_SECONDS = 60;

	private final int limit;
	private final long windowMillis;
	private final ObjectMapper json;
	private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

	public AiRateLimitFilter(ObjectMapper json,
			@Value("${AI_RATE_LIMIT:" + DEFAULT_LIMIT + "}") int limit,
			@Value("${AI_RATE_WINDOW_SECONDS:" + DEFAULT_WINDOW_SECONDS + "}") int windowSeconds) {
		this.json = json;
		this.limit = Math.max(1, limit);
		this.windowMillis = Math.max(1, windowSeconds) * 1_000L;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		// Only AI-costing endpoints are guarded; auth/progress/uploads stay open.
		return !(path.startsWith("/api/lesson/") || path.startsWith("/api/assessment/"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String identity = identityOf(request);
		long now = System.currentTimeMillis();

		if (!tryAcquire(identity, now)) {
			long retryAfterSeconds = Math.max(1, windowMillis / 1_000);
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
			response.setContentType("application/json");
			response.getWriter().write(json.writeValueAsString(Map.of(
					"error", "Too many AI requests. Please wait " + retryAfterSeconds + " seconds and try again.")));
			return;
		}
		chain.doFilter(request, response);
	}

	/** Records the request and reports whether it fits inside the window. */
	private boolean tryAcquire(String identity, long now) {
		Deque<Long> hits = buckets.computeIfAbsent(identity, key -> new ArrayDeque<>());
		synchronized (hits) {
			while (!hits.isEmpty() && now - hits.peekFirst() >= windowMillis) {
				hits.pollFirst();
			}
			if (hits.size() >= limit) {
				return false;
			}
			hits.addLast(now);
		}
		sweepIfNeeded(now);
		return true;
	}

	private volatile long lastSweep = System.currentTimeMillis();

	/** Occasionally drops empty/stale buckets so memory stays bounded. */
	private void sweepIfNeeded(long now) {
		if (now - lastSweep < windowMillis) {
			return;
		}
		lastSweep = now;
		buckets.values().removeIf(hits -> {
			synchronized (hits) {
				return hits.isEmpty() || now - hits.peekLast() >= windowMillis;
			}
		});
	}

	private static String identityOf(HttpServletRequest request) {
		String email = request.getHeader("X-Student-Email");
		if (email != null && !email.isBlank()) {
			return "email:" + email.trim().toLowerCase();
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
		return "ip:" + ip;
	}
}
