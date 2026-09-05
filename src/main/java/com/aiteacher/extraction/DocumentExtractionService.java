package com.aiteacher.extraction;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.apache.tika.Tika;
import org.apache.tika.exception.EncryptedDocumentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Dedicated extraction service for real user-uploaded learning material.
 * Responsible for every step before the AI sees the content:
 *
 * <ol>
 *   <li>validate the upload (file present, extension, MIME type, size),</li>
 *   <li>route to the format-specific {@link DocumentExtractor},</li>
 *   <li>verify meaningful text was actually extracted,</li>
 *   <li>map every failure to a user-friendly message.</li>
 * </ol>
 *
 * Uploaded bytes are processed in memory and never stored on disk, and
 * uploaded content is never executed. All messages are safe to show to the
 * student — no stack traces, paths or provider details.
 */
@Service
public class DocumentExtractionService {

	public static final String UNSUPPORTED_MESSAGE = "Unsupported file type. Please upload PDF, TXT, PPT, or PPTX.";
	public static final String TOO_LARGE_MESSAGE = "File is too large. Please upload a smaller file.";
	public static final String EMPTY_MESSAGE = "Please choose a file to upload.";
	public static final String NO_TEXT_MESSAGE = "No readable text could be extracted from this file.";
	public static final String UNREADABLE_MESSAGE = "This file is password-protected or could not be read.";
	public static final String PARSE_FAILED_MESSAGE = "Could not read this file. Please upload a valid PDF, TXT, PPT, or PPTX file.";

	/** MIME types that must never be accepted as learning material. */
	private static final List<String> BLOCKED_MIME_TYPES = List.of(
			"application/x-msdownload",
			"application/x-msdos-program",
			"application/x-msi",
			"application/x-dosexec",
			"application/vnd.microsoft.portable-executable",
			"application/x-executable",
			"application/x-sh",
			"application/x-shellscript",
			"text/x-shellscript",
			"application/x-mach-binary",
			"application/x-elf");

	private final List<DocumentExtractor> extractors;
	private final int maxSizeBytes;

	public DocumentExtractionService(List<DocumentExtractor> extractors,
			@Value("${aiteacher.upload.max-size-bytes:10485760}") int maxSizeBytes) {
		this.extractors = extractors;
		this.maxSizeBytes = maxSizeBytes;
	}

	/**
	 * Validates the uploaded file and extracts its real content.
	 *
	 * @throws IllegalArgumentException with a user-friendly message for every
	 *         validation or extraction failure
	 */
	public ExtractedDocument extract(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException(EMPTY_MESSAGE);
		}
		String fileName = file.getOriginalFilename();
		if (fileName == null || fileName.isBlank()) {
			throw new IllegalArgumentException(EMPTY_MESSAGE);
		}

		DocumentExtractor extractor = findExtractor(fileName);
		if (extractor == null) {
			throw new IllegalArgumentException(UNSUPPORTED_MESSAGE);
		}
		if (file.getSize() > maxSizeBytes) {
			throw new IllegalArgumentException(TOO_LARGE_MESSAGE);
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (Exception ex) {
			throw new IllegalArgumentException(UNREADABLE_MESSAGE);
		}
		rejectExecutableContent(bytes, fileName);

		try (InputStream content = new ByteArrayInputStream(bytes)) {
			ExtractedDocument document = extractor.extract(fileName, content);
			if (document.extractedText() == null || document.extractedText().isBlank()) {
				throw new IllegalArgumentException(NO_TEXT_MESSAGE);
			}
			return document;
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (EncryptedDocumentException ex) {
			throw new IllegalArgumentException(UNREADABLE_MESSAGE);
		} catch (Exception ex) {
			throw new IllegalArgumentException(PARSE_FAILED_MESSAGE);
		}
	}

	private DocumentExtractor findExtractor(String fileName) {
		return extractors.stream()
				.filter(extractor -> extractor.supports(fileName))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Second line of defence beyond the extension whitelist: the actual bytes
	 * are sniffed with Apache Tika and known executable/script formats are
	 * rejected even when the file name claims to be a PDF/TXT/PPT.
	 */
	private void rejectExecutableContent(byte[] bytes, String fileName) {
		try {
			String detected = new Tika().detect(bytes, fileName);
			if (detected != null && BLOCKED_MIME_TYPES.contains(detected.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException(UNSUPPORTED_MESSAGE);
			}
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (Exception ignored) {
			// Detection is best-effort; the extension whitelist already applied.
		}
	}
}