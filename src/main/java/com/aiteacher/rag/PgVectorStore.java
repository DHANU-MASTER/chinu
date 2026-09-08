package com.aiteacher.rag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

/**
 * Production {@link VectorStore} backed by PostgreSQL + the
 * <a href="https://github.com/pgvector/pgvector">pgvector</a> extension.
 *
 * <p>Chunks and their embedding vectors persist in the {@code rag_chunks}
 * table; similarity search uses pgvector's cosine-distance operator
 * {@code <=>} so the ranking happens inside the database (indexable with an
 * HNSW/IVFFlat index at scale, though a linear scan is plenty for
 * lecture-sized documents).</p>
 *
 * <p>Vectors are stored without a fixed dimension so different embedding
 * models can be used; mixing dimensions inside one {@code material_id} is
 * prevented by deleting that document's rows before re-indexing.</p>
 */
@Component
public class PgVectorStore implements VectorStore {

    private final DataSource dataSource;
    private volatile boolean schemaReady;

    public PgVectorStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return true when the pgvector extension is usable and the
     *         {@code rag_chunks} table exists (or was just created).
     */
    public boolean isAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            boolean hasVectorExtension;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
                try (ResultSet rs = statement.executeQuery()) {
                    hasVectorExtension = rs.next();
                }
            }
            if (!hasVectorExtension) {
                return false;
            }
            ensureSchema(connection);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void put(String materialId, List<DocumentChunk> chunks, List<double[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks and vectors must have the same size");
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM rag_chunks WHERE material_id = ?")) {
                    delete.setString(1, materialId);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO rag_chunks (material_id, chunk_index, chunk_text, embedding) VALUES (?, ?, ?, ?::vector)")) {
                    for (int i = 0; i < chunks.size(); i++) {
                        insert.setString(1, materialId);
                        insert.setInt(2, chunks.get(i).index());
                        insert.setString(3, chunks.get(i).text());
                        insert.setString(4, vectorLiteral(vectors.get(i)));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to index document into pgvector: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ScoredChunk> search(String materialId, double[] queryVector, int topK) {
        String sql = "SELECT chunk_index, chunk_text, 1 - (embedding <=> ?::vector) AS similarity "
                + "FROM rag_chunks WHERE material_id = ? "
                + "ORDER BY embedding <=> ?::vector LIMIT ?";
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            String queryLiteral = vectorLiteral(queryVector);
            statement.setString(1, queryLiteral);
            statement.setString(2, materialId);
            statement.setString(3, queryLiteral);
            statement.setInt(4, topK);
            try (ResultSet rs = statement.executeQuery()) {
                List<ScoredChunk> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new ScoredChunk(
                            new DocumentChunk(rs.getInt("chunk_index"), rs.getString("chunk_text")),
                            rs.getDouble("similarity")));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("pgvector similarity search failed: " + e.getMessage(), e);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = dataSource.getConnection();
        ensureSchema(connection);
        return connection;
    }

    /** Creates the chunk table once per process; cheap no-op afterwards. */
    private void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS rag_chunks ("
                            + "material_id TEXT NOT NULL, "
                            + "chunk_index INT NOT NULL, "
                            + "chunk_text TEXT NOT NULL, "
                            + "embedding VECTOR NOT NULL, "
                            + "PRIMARY KEY (material_id, chunk_index))")) {
                statement.executeUpdate();
            }
            schemaReady = true;
        }
    }

    /**
     * Renders a normalized vector as a pgvector literal: {@code [0.12,-0.3,...]}.
     * Locale.ROOT guarantees '.' decimal separators regardless of host locale.
     */
    static String vectorLiteral(double[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.7f", vector[i]));
        }
        return sb.append(']').toString();
    }
}
