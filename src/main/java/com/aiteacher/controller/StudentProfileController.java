package com.aiteacher.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiteacher.dto.StudentProfileRequest;

/**
 * Phase 1 REST API: accepts a real student profile and echoes it back.
 * No AI calls, no persistence, no hardcoded data.
 */
@RestController
@RequestMapping("/api")
public class StudentProfileController {

	/**
	 * POST /api/profile — validate the six required fields and return the
	 * submitted profile as JSON. Responds 400 when any field is missing or blank.
	 */
	@PostMapping("/profile")
	public ResponseEntity<?> createProfile(@RequestBody StudentProfileRequest request) {
		if (isBlank(request.getName())) {
			return badRequest("name must not be empty");
		}
		if (isBlank(request.getEducationLevel())) {
			return badRequest("educationLevel must not be empty");
		}
		if (isBlank(request.getLanguage())) {
			return badRequest("language must not be empty");
		}
		if (isBlank(request.getTeachingStyle())) {
			return badRequest("teachingStyle must not be empty");
		}
		if (isBlank(request.getObjective())) {
			return badRequest("objective must not be empty");
		}
		// Topic is optional when the student supplies uploaded learning material.
		if (isBlank(request.getTopic()) && isBlank(request.getUploadedMaterial())) {
			return badRequest("topic must not be empty");
		}
		return ResponseEntity.ok(request);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ResponseEntity<Map<String, String>> badRequest(String message) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
	}
}