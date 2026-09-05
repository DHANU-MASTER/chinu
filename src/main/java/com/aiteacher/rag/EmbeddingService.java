package com.aiteacher.rag;

/**
 * Turns text into a fixed-size numeric vector so that semantic similarity can
 * be computed with cosine distance. The implementation must be deterministic:
 * the same text always produces the same vector.
 */
public interface EmbeddingService {

    /**
     * @param text any text (may be {@code null} or blank)
     * @return a fixed-size vector; never {@code null}
     */
    double[] embed(String text);
}