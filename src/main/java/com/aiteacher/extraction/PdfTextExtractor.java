package com.aiteacher.extraction;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

/**
 * Extracts readable text from a real uploaded PDF via Apache Tika (which uses
 * PDFBox under the hood). The page count is read straight from the actual
 * document structure with PDFBox, never from a hardcoded value.
 */
@Component
public class PdfTextExtractor implements DocumentExtractor {

	@Override
	public boolean supports(String fileName) {
		return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
	}

	@Override
	public ExtractedDocument extract(String fileName, InputStream content) throws Exception {
		byte[] bytes = content.readAllBytes();

		Metadata metadata = new Metadata();
		String text = new Tika().parseToString(new ByteArrayInputStream(bytes), metadata);

		Integer pageCount = pdfBoxPageCount(bytes);
		if (pageCount == null) {
			pageCount = metadataPageCount(metadata);
		}

		String cleaned = TextCleaner.clean(text);
		return new ExtractedDocument(fileName, metadata.get(Metadata.CONTENT_TYPE), cleaned.length(), pageCount, cleaned);
	}

	/** Reads the page count straight from the PDF structure (PDFBox 3). */
	private Integer pdfBoxPageCount(byte[] bytes) {
		try (PDDocument document = Loader.loadPDF(bytes)) {
			return document.getNumberOfPages();
		} catch (Exception ex) {
			return null;
		}
	}

	/** Falls back to Tika's page metadata. */
	private Integer metadataPageCount(Metadata metadata) {
		for (String key : new String[] { "xmpTPg:NPages", "Page-Count" }) {
			String value = metadata.get(key);
			if (value != null) {
				try {
					return Integer.parseInt(value.trim());
				} catch (NumberFormatException ignored) {
					// try the next metadata key
				}
			}
		}
		return null;
	}
}