package com.aiteacher.rag;

/**
 * One chunk of a larger document, produced by {@link ChunkingService}.
 * {@code index} is the chunk's position within the source document (0-based).
 */
public record DocumentChunk(int index, String text) {
}