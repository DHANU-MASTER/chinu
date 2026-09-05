package com.aiteacher.rag;

/**
 * A {@link DocumentChunk} together with its cosine-similarity score against
 * the retrieval query. Higher scores mean stronger relevance.
 */
public record ScoredChunk(DocumentChunk chunk, double score) {
}