package com.aiteacher.extraction;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

/**
 * Extracts readable text from a real uploaded PowerPoint file (.ppt and .pptx)
 * via Apache Tika. The slide count is read from the actual presentation
 * structure with Apache POI (XMLSlideShow for .pptx, HSLFSlideShow for .ppt).
 */
@Component
public class PowerPointTextExtractor implements DocumentExtractor {

	@Override
	public boolean supports(String fileName) {
		if (fileName == null) {
			return false;
		}
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".ppt") || lower.endsWith(".pptx");
	}

	@Override
	public ExtractedDocument extract(String fileName, InputStream content) throws Exception {
		byte[] bytes = content.readAllBytes();
		String lower = fileName.toLowerCase(Locale.ROOT);

		Metadata metadata = new Metadata();
		String text = new Tika().parseToString(new ByteArrayInputStream(bytes), metadata);

		Integer pageCount = slideCount(bytes, lower);
		if (pageCount == null) {
			pageCount = metadataPageCount(metadata);
		}

		String cleaned = TextCleaner.clean(text);
		return new ExtractedDocument(fileName, metadata.get(Metadata.CONTENT_TYPE), cleaned.length(), pageCount, cleaned);
	}

	/** Reads the number of slides straight from the presentation structure. */
	private Integer slideCount(byte[] bytes, String lower) {
		try {
			if (lower.endsWith(".pptx")) {
				try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
					return show.getSlides().size();
				}
			}
			try (HSLFSlideShow show = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
				return show.getSlides().size();
			}
		} catch (Exception ex) {
			return null;
		}
	}

	private Integer metadataPageCount(Metadata metadata) {
		for (String key : new String[] { "Page-Count", "xmpTPg:NPages" }) {
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