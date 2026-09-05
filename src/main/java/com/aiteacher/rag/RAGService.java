package com.aiteacher.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Practical, hackathon-friendly RAG pipeline:
 *
 * <pre>
 *   User material → text extraction (already done upstream)
 *   → chunking → local embeddings → vector storage → similarity retrieval
 *   → grounded context for the AI prompt
 * </pre>
 *
 * <p>No external services are used: chunks are embedded with the deterministic
 * {@link LocalHashEmbeddingService} and stored in the {@link InMemoryVectorStore}.
 * Documents are indexed once (cached by a stable hash of their text) and
 * searched per query. {@link #buildContext} formats the retrieved excerpts so
 * the AI can ground its lesson in the student's actual material without ever
 * receiving the whole document.</p>
 */
@Service
public class RAGService implements RetrievalService {

    /** Guard against pathological documents; far beyond any lecture-sized file. */
    private static final int MAX_CHUNKS_PER_DOCUMENT = 1_000;

    private final ChunkingService chunking;
    private final EmbeddingService embedding;
    private final VectorStore vectorStore;

    private final Map<String, List<DocumentChunk>> indexCache = new ConcurrentHashMap<>();

    public RAGService() {
        this(new ParagraphChunkingService(), new LocalHashEmbeddingService(), new InMemoryVectorStore());
    }

    RAGService(ChunkingService chunking, EmbeddingService embedding, VectorStore vectorStore) {
        this.chunking = chunking;
        this.embedding = embedding;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<DocumentChunk> retrieve(String material, String query, int topK) {
        if (material == null || material.isBlank() || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        String materialId = stableId(material);
        indexIfNeeded(materialId, material);

        double[] queryVector = embedding.embed(query);
        List<ScoredChunk> scored = vectorStore.search(materialId, queryVector, topK);
        List<DocumentChunk> result = new ArrayList<>(scored.size());
        for (ScoredChunk s : scored) {
            result.add(s.chunk());
        }
        return result;
    }

    /**
     * Retrieves the top-k chunks and formats them as a labeled context block for
     * an AI prompt. Returns {@code null} when there is nothing to ground on.
     *
     * @param material the extracted document text
     * @param query the student/lesson context the retrieval should match
     * @param topK maximum number of excerpts
     * @param maxChars hard cap on the returned context (0 = unlimited)
     */
    public String buildContext(String material, String query, int topK, int maxChars) {
        List<DocumentChunk> chunks = retrieve(material, query, topK);
        if (chunks.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            sb.append("[Excerpt ").append(chunk.index() + 1).append("]\n")
                    .append(chunk.text().trim()).append("\n\n");
        }
        String context = sb.toString().trim();
        if (maxChars > 0 && context.length() > maxChars) {
            String cut = context.substring(0, maxChars);
            int lastSpace = cut.lastIndexOf(' ');
            context = lastSpace > maxChars * 3 / 4 ? cut.substring(0, lastSpace) : cut;
        }
        return context;
    }

    private void indexIfNeeded(String materialId, String material) {
        indexCache.computeIfAbsent(materialId, id -> {
            List<DocumentChunk> chunks = chunking.chunk(material);
            if (chunks.size() > MAX_CHUNKS_PER_DOCUMENT) {
                chunks = chunks.subList(0, MAX_CHUNKS_PER_DOCUMENT);
            }
            if (!chunks.isEmpty()) {
                List<double[]> vectors = new ArrayList<>(chunks.size());
                for (DocumentChunk chunk : chunks) {
                    vectors.add(embedding.embed(chunk.text()));
                }
                vectorStore.put(id, chunks, vectors);
            }
            return chunks;
        });
    }

    /** Stable identifier for a document, derived from its text (dedupes re-uploads). */
    private static String stableId(String material) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < material.length(); i++) {
            hash ^= material.charAt(i);
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash);
    }
}