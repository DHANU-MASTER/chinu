package com.aiteacher.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.aiteacher.extraction.DocumentExtractionService;

/**
 * Maps upload failures that Spring raises before any controller method runs
 * (e.g. the configured multipart size limit) to a user-friendly message.
 * Internal details are never exposed.
 */
@RestControllerAdvice
public class UploadExceptionHandler {

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(Map.of("error", DocumentExtractionService.TOO_LARGE_MESSAGE));
	}
}