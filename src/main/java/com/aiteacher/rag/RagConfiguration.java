package com.aiteacher.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chooses the active {@link EmbeddingService} and {@link VectorStore}
 * implementation from configuration instead of hardcoding them:
 *
 * <pre>
 *   RAG_EMBEDDINGS = api    → {@link ApiEmbeddingService} + {@link PgVectorStore}
 *   anything else (default) → {@link LocalHashEmbeddingService} + {@link InMemoryVectorStore}
 * </pre>
 *
 * <p>Both real implementations require PostgreSQL with the {@code pgvector}
 * extension, so they only make sense under the {@code postgres} profile; the
 * compose file wires them together. The local pair remains the default because
 * it works with zero setup and keeps every existing test meaningful.</p>
 */
@Service
public class RagConfiguration {

    /** Parsed view of the RAG configuration, injected into {@link RAGService}. */
    public record Settings(boolean useApiEmbeddings) {
    }

    private final Settings settings;

    public RagConfiguration(@Value("${RAG_EMBEDDINGS:local}") String mode) {
        this.settings = new Settings("api".equalsIgnoreCase(mode == null ? "" : mode.trim()));
    }

    public Settings settings() {
        return settings;
    }

    /**
     * The active embedding service: the API client when enabled, otherwise the
     * deterministic local implementation.
     */
    public EmbeddingService embeddingService(ObjectProvider<ApiEmbeddingService> apiEmbeddings,
            LocalHashEmbeddingService local) {
        if (settings.useApiEmbeddings()) {
            return apiEmbeddings.getObject();
        }
        return local;
    }

    /**
     * The active vector store. pgvector requires PostgreSQL; under any other
     * datasource the API mode would fail at first query, so it is refused here
     * with a clear message instead.
     */
    public VectorStore vectorStore(ObjectProvider<PgVectorStore> pgVectorStore, InMemoryVectorStore inMemory) {
        if (settings.useApiEmbeddings()) {
            PgVectorStore store = pgVectorStore.getObject();
            if (!store.isAvailable()) {
                throw new IllegalStateException(
                        "RAG_EMBEDDINGS=api requires PostgreSQL with the pgvector extension (vector type not available). "
                                + "Load it with 'CREATE EXTENSION IF NOT EXISTS vector;' or run docker-compose.yml, which does it for you.");
            }
            return store;
        }
        return inMemory;
    }
}
