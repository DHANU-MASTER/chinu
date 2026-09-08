package com.aiteacher.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for the {@link AiRateLimitFilter} sliding window, driving the
 * filter directly with Spring's mock request/response objects.
 */
class AiRateLimitFilterTests {

	private static final long WINDOW_MS = 60_000;

	private AiRateLimitFilter filter;
	private ObjectMapper json;

	@BeforeEach
	void setUp() {
		json = new ObjectMapper();
		// Small limit so tests can exhaust the window quickly; 5 requests / 60s
		filter = new AiRateLimitFilter(json, 5, 60);
	}

	private MockHttpServletResponse call(String uri, String email, String remoteAddr) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
		if (email != null) {
			request.addHeader("X-Student-Email", email);
		}
		request.setRemoteAddr(remoteAddr);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		return response;
	}

	private MockHttpServletResponse streamedResponse(MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		return response;
	}

	@Test
	void allowsRequestsBelowTheLimitAndCountsPerIdentity() throws Exception {
		for (int i = 0; i < 5; i++) {
			MockHttpServletResponse response = call("/api/lesson/plan", "student@example.com", "10.0.0.1");
			assertNotEquals(429, response.getStatus(), "request " + i + " should pass");
		}
		MockHttpServletResponse sixth = call("/api/lesson/plan", "student@example.com", "10.0.0.1");
		assertEquals(429, sixth.getStatus(), "the 6th request inside the window must be limited");
	}

	@Test
	void differentIdentitiesHaveIndependentBuckets() throws Exception {
		for (int i = 0; i < 5; i++) {
			call("/api/lesson/plan", "student-a@example.com", "10.0.0.1");
		}
		MockHttpServletResponse other = call("/api/lesson/plan", "student-b@example.com", "10.0.0.1");
		assertNotEquals(429, other.getStatus(), "a different email must have its own bucket");
	}

	@Test
	void anonymousTrafficIsLimitedByIp() throws Exception {
		for (int i = 0; i < 5; i++) {
			call("/api/lesson/plan", null, "10.9.9.9");
		}
		assertEquals(429, call("/api/lesson/plan", null, "10.9.9.9").getStatus(), "same IP exhausts the bucket");
		assertNotEquals(429, call("/api/lesson/plan", null, "10.9.9.10").getStatus(), "another IP is unaffected");
	}	@Test
	void windowExpiryReopensTheBucket() throws Exception {
		AiRateLimitFilter tightWindow = new AiRateLimitFilter(json, 1, 1); // 1 request / 1 second
		for (int i = 0; i < 1; i++) {
			MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/lesson/plan");
			request.addHeader("X-Student-Email", "window@example.com");
			MockHttpServletResponse response = new MockHttpServletResponse();
			tightWindow.doFilter(request, response, new MockFilterChain());
			assertNotEquals(429, response.getStatus());
		}
		// Immediately again → limited
		MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/lesson/plan");
		blocked.addHeader("X-Student-Email", "window@example.com");
		MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
		tightWindow.doFilter(blocked, blockedResponse, new MockFilterChain());
		assertEquals(429, blockedResponse.getStatus());

		// After the 1s window passes → allowed again
		Thread.sleep(1_100);
		MockHttpServletRequest after = new MockHttpServletRequest("POST", "/api/lesson/plan");
		after.addHeader("X-Student-Email", "window@example.com");
		MockHttpServletResponse afterResponse = new MockHttpServletResponse();
		tightWindow.doFilter(after, afterResponse, new MockFilterChain());
		assertNotEquals(429, afterResponse.getStatus(), "the window must reopen after expiry");
	}

	@Test
	void limitedResponseCarriesRetryAfterAndJsonBody() throws Exception {
		for (int i = 0; i < 5; i++) {
			call("/api/lesson/plan", "headers@example.com", "10.0.0.1");
		}
		MockHttpServletResponse limited = call("/api/lesson/plan", "headers@example.com", "10.0.0.1");
		assertEquals(429, limited.getStatus());
		assertEquals("60", limited.getHeader("Retry-After"));
		String body = limited.getContentAsString();
		assertTrue(body.contains("Too many AI requests"), "the body should explain the limit");
	}

	@Test
	void nonAiPathsBypassTheFilter() throws Exception {
		for (int i = 0; i < 50; i++) {
			call("/api/auth/login", "login@example.com", "10.0.0.1");
		}
		MockHttpServletResponse response = call("/api/auth/login", "login@example.com", "10.0.0.1");
		assertNotEquals(429, response.getStatus(), "auth endpoints must never be AI-rate-limited");
	}

	@Test
	void xForwardedForTakesPrecedenceOverRemoteAddr() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/lesson/plan");
		request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
		request.setRemoteAddr("10.0.0.1");
		// Exhaust 203.0.113.7's bucket
		for (int i = 0; i < 5; i++) {
			streamedResponse(request);
		}
		assertEquals(429, streamedResponse(request).getStatus(), "the forwarded IP should be limited");

		MockHttpServletRequest otherIp = new MockHttpServletRequest("POST", "/api/lesson/plan");
		otherIp.setRemoteAddr("203.0.113.8");
		assertNotEquals(429, streamedResponse(otherIp).getStatus(), "a different forwarded IP is unaffected");
	}

	@Test
	void defaultsAreSane() {
		assertEquals(30, AiRateLimitFilter.DEFAULT_LIMIT);
		assertEquals(60, AiRateLimitFilter.DEFAULT_WINDOW_SECONDS);
		assertTrue(WINDOW_MS > 0);
	}
}
