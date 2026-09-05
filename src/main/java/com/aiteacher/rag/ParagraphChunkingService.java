package com.aiteacher.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, language-agnostic chunking: text is split on paragraph and
 * sentence boundaries into segments, which are then packed into chunks of at
 * most {@link #MAX_CHUNK_CHARS} characters. Consecutive chunks overlap by up
 * to {@link #OVERLAP_CHARS} characters so that retrieval does not lose
 * concepts split across a boundary.
 */
public class ParagraphChunkingService implements ChunkingService {

    static final int MAX_CHUNK_CHARS = 1200;
    static final int OVERLAP_CHARS = 200;

    @Override
    public List<DocumentChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> segments = splitSegments(text);
        if (segments.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String segment : segments) {
            if (current.length() > 0 && current.length() + 1 + segment.length() > MAX_CHUNK_CHARS) {
                chunks.add(new DocumentChunk(index++, current.toString().trim()));
                String overlap = trailingWords(current.toString(), OVERLAP_CHARS);
                current = new StringBuilder(overlap);
                if (!overlap.isEmpty()) {
                    current.append(' ');
                }
            }
            current.append(segment).append(' ');
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            chunks.add(new DocumentChunk(index, last));
        }
        return chunks;
    }

    /** Paragraphs first, then sentences inside long paragraphs; hard-caps any oversized segment. */
    private List<String> splitSegments(String text) {
        List<String> segments = new ArrayList<>();
        for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\\n\\s*\\n")) {
            if (paragraph.isBlank()) {
                continue;
            }
            for (String sentence : paragraph.split("(?<=[.!?\\u0964])\\s+|\\n+")) {
                String segment = sentence.trim();
                if (segment.isEmpty()) {
                    continue;
                }
                while (segment.length() > MAX_CHUNK_CHARS) {
                    segments.add(segment.substring(0, MAX_CHUNK_CHARS).trim());
                    segment = segment.substring(MAX_CHUNK_CHARS).trim();
                }
                if (!segment.isEmpty()) {
                    segments.add(segment);
                }
            }
        }
        return segments;
    }

    /** The tail of {@code s} (up to {@code chars} long), cut at a word boundary. */
    private static String trailingWords(String s, int chars) {
        if (s.length() <= chars) {
            return s;
        }
        int cut = s.length() - chars;
        int space = s.indexOf(' ', cut);
        return space > 0 ? s.substring(space + 1) : s.substring(cut);
    }
}