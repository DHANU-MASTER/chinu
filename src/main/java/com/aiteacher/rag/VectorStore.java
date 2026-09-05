package com.aiteacher.rag;

import java.util.List;

/**
 * Stores chunk vectors per document and returns the most similar chunks for a
 * query vector. Kept as a small abstraction so the in-memory implementation can
 * later be swapped for a real vector database without touching callers.
 */
public interface VectorStore {

    /**
     * Index (or replace) the chunks of one document.
     *
     * @param materialId stable identifier of the document (e.g. hash of its text)
     * @param chunks the chunks, in document order
     * @param vectors one vector per chunk, same order and size
     */
    void put(String materialId, List<DocumentChunk> chunks, List<double[]> vectors);

    /**
     * @param materialId the document to search within
     * @param queryVector normalized query vector
     * @param topK maximum number of results
     * @return the {@code topK} most similar chunks, best first
     */
    List<ScoredChunk> search(String materialId, double[] queryVector, int topK);
}