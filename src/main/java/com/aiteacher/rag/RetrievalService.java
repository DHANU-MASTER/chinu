package com.aiteacher.rag;

import java.util.List;

/**
 * Retrieves the most relevant parts of a document for a query.
 */
public interface RetrievalService {

    /**
     * @param material the raw document text (may be {@code null} or blank)
     * @param query the student/lesson context the retrieval should match
     * @param topK maximum number of chunks to return
     * @return the most relevant chunks, best first; never {@code null}
     */
    List<DocumentChunk> retrieve(String material, String query, int topK);
}