package com.aiteacher.extraction;

/**
 * Structured result of extracting text from a real user-uploaded file.
 * Every value is derived from the actual uploaded file — never from
 * hardcoded sample documents.
 *
 * @param fileName       original file name as uploaded by the user
 * @param fileType       detected MIME type of the uploaded bytes
 * @param characterCount length of the extracted text
 * @param pageCount      number of pages/slides, or {@code null} when the
 *                       format has no page concept (e.g. TXT)
 * @param extractedText  the readable text pulled from the file
 */
public record ExtractedDocument(String fileName, String fileType, int characterCount, Integer pageCount,
		String extractedText) {
}