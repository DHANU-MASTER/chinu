package com.aiteacher.rag;

import java.util.List;

/**
 * Splits a document's raw text into smaller, self-contained {@link DocumentChunk}s
 * so that retrieval can find the most relevant part of a large document.
 */
public interface ChunkingService {

    /**
     * @param text the extracted document text (may be {@code null} or blank)
     * @return an ordered list of chunks; never {@code null}, may be empty
     */
    List<DocumentChunk> chunk(String text);
}