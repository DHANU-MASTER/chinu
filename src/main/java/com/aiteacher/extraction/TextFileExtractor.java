package com.aiteacher.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Extracts text from a real uploaded TXT file. The file is read as UTF-8 with
 * replacement of invalid byte sequences, so a slightly malformed file still
 * yields its meaningful content instead of failing the whole request.
 */
@Component
public class TextFileExtractor implements DocumentExtractor {

	@Override
	public boolean supports(String fileName) {
		return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".txt");
	}

	@Override
	public ExtractedDocument extract(String fileName, InputStream content) throws IOException {
		byte[] bytes = content.readAllBytes();
		String text = decodeUtf8Lenient(bytes);
		String cleaned = TextCleaner.clean(text);
		// TXT has no page concept — pageCount stays null.
		return new ExtractedDocument(fileName, "text/plain", cleaned.length(), null, cleaned);
	}

	private String decodeUtf8Lenient(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE)
					.decode(ByteBuffer.wrap(bytes))
					.toString();
		} catch (CharacterCodingException e) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}
}