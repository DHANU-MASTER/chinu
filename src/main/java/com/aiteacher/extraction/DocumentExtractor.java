package com.aiteacher.extraction;

import java.io.InputStream;

/**
 * Extracts readable text from one family of uploaded documents. Implementations
 * are format-specific ({@link PdfTextExtractor}, {@link TextFileExtractor},
 * {@link PowerPointTextExtractor}) and are selected by
 * {@link DocumentExtractionService} based on the real uploaded file.
 */
public interface DocumentExtractor {

	/**
	 * Whether this extractor can handle the given original file name
	 * (extension-based routing).
	 */
	boolean supports(String fileName);

	/**
	 * Extracts the readable text and page/slide metadata from the actual
	 * uploaded content. Must never invent content: the returned text is exactly
	 * what the file contains.
	 *
	 * @throws Exception when the file cannot be parsed (corrupted,
	 *         password-protected, truncated, ...)
	 */
	ExtractedDocument extract(String fileName, InputStream content) throws Exception;
}