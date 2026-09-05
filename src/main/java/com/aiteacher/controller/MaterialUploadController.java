package com.aiteacher.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aiteacher.extraction.DocumentExtractionService;
import com.aiteacher.extraction.ExtractedDocument;

/**
 * Phase 4 REST API: receives a real user-uploaded learning file
 * ({@code multipart/form-data}, field {@code file}), validates it, extracts the
 * actual content via {@link DocumentExtractionService} and returns the
 * structured extraction result. No extraction logic lives in this controller.
 */
@RestController
@RequestMapping("/api")
public class MaterialUploadController {

	private final DocumentExtractionService extractionService;

	public MaterialUploadController(DocumentExtractionService extractionService) {
		this.extractionService = extractionService;
	}

	/**
	 * POST /api/material/upload — extracts readable text from a real PDF, TXT,
	 * PPT or PPTX file.
	 *
	 * @return {@code { "fileName", "fileType", "characterCount", "pageCount", "extractedText" }}
	 */
	@PostMapping(value = "/material/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ExtractedDocument uploadMaterial(@RequestParam("file") MultipartFile file) {
		return extractionService.extract(file);
	}

	/** Maps validation/extraction failures to a user-safe 400 message. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
	}
}