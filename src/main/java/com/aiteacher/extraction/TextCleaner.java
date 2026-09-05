package com.aiteacher.extraction;

/**
 * Removes common formatting artifacts left behind by document parsers
 * (repeated blank lines, stray whitespace) without altering the actual content.
 */
final class TextCleaner {

	private TextCleaner() {
	}

	static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String text = raw.replace("\r\n", "\n").replace('\r', '\n');
		// Collapse runs of blank lines into a single line break.
		text = text.replaceAll("\n[ \t]*\n(?:[ \t]*\n)+", "\n\n");
		return text.trim();
	}
}