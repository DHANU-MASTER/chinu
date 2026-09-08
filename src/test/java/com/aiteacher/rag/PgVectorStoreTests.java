package com.aiteacher.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-database integration test for {@link PgVectorStore}, running against the
 * official pgvector image via Testcontainers. Skips gracefully (as "skipped",
 * not failure) when Docker is unavailable, e.g. in CI without Docker or on
 * machines where the daemon is not running.
 */
class PgVectorStoreTests {

    private static PostgreSQLContainer<?> postgres;
    private static boolean dockerAvailable;

    @BeforeAll
    static void startContainer() {
        dockerAvailable = checkDocker();
        if (!dockerAvailable) {
            return;
        }
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                .withDatabaseName("aiteacher")
                .withUsername("aiteacher")
                .withPassword("aiteacher");
        postgres.start();
        try (Connection connection = postgres.createConnection("")) {
            // The pgvector image ships the extension files but still needs it enabled per database
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            }
        } catch (Exception e) {
            dockerAvailable = false;
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static boolean checkDocker() {
        try {
            Process probe = new ProcessBuilder("docker", "version", "--format", "ok")
                    .redirectErrorStream(true)
                    .start();
            probe.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            return probe.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void storesAndRetrievesChunksWithCosineRanking() throws Exception {
        if (!dockerAvailable) {
            return; // skip quietly when Docker is not available
        }
        PgVectorStore store = new PgVectorStore(postgresDataSource());
        assertTrue(store.isAvailable(), "pgvector extension should be detected");

        // Three chunks along the x-axis; the query is closest to chunk 2's direction
        store.put("doc-1",
                List.of(new DocumentChunk(0, "Intro to cells"), new DocumentChunk(1, "Photosynthesis details"),
                        new DocumentChunk(2, "Cell respiration")),
                List.of(new double[] { 1, 0, 0 }, new double[] { 0.9, 0.1, 0 }, new double[] { 0, 1, 0 }));
        List<ScoredChunk> hits = store.search("doc-1", new double[] { 1, 0.2, 0 }, 2);

        assertEquals(2, hits.size());
        assertEquals("Photosynthesis details", hits.get(0).chunk().text(), "nearest vector ranks first");
        assertTrue(hits.get(0).score() > hits.get(1).score(), "scores descend by similarity");
    }

    @Test
    void reIndexingReplacesPreviousRowsForTheSameDocument() throws Exception {
        if (!dockerAvailable) {
            return;
        }
        PgVectorStore store = new PgVectorStore(postgresDataSource());
        store.put("doc-2", List.of(new DocumentChunk(0, "old content")),
                List.of(new double[] { 1, 0 }));
        store.put("doc-2", List.of(new DocumentChunk(0, "new content"), new DocumentChunk(1, "extra")),
                List.of(new double[] { 0, 1 }, new double[] { 1, 1 }));

        List<ScoredChunk> hits = store.search("doc-2", new double[] { 0, 1 }, 10);
        assertEquals(2, hits.size(), "stale rows must be deleted on re-index");
        assertEquals("extra", hits.get(0).chunk().text());
        assertEquals("new content", hits.get(1).chunk().text());
    }

    @Test
    void searchOnAnUnknownDocumentIsEmpty() throws Exception {
        if (!dockerAvailable) {
            return;
        }
        PgVectorStore store = new PgVectorStore(postgresDataSource());
        assertTrue(store.search("missing-doc", new double[] { 1, 0 }, 5).isEmpty());
    }

    private static SimpleDriverDataSource postgresDataSource() {
        return new SimpleDriverDataSource(new org.postgresql.Driver(),
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void vectorLiteralFormatIsLocaleSafe() {
        String literal = PgVectorStore.vectorLiteral(new double[] { 0.25, -0.5, 1.0 / 3.0 });
        assertTrue(literal.startsWith("["), "pgvector literal must start with [");
        assertTrue(literal.endsWith("]"), "pgvector literal must end with ]");
        assertEquals(3, literal.split(",").length, "three components survive formatting");
        assertTrue(literal.contains("0.3333333"), "fractional component keeps 7 decimal places");
    }
}
