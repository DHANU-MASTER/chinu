package com.aiteacher.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * Integration tests for {@link ApiEmbeddingService} against a stub
 * OpenAI-compatible {@code /embeddings} server (no network access needed).
 */
class ApiEmbeddingServiceTests {

    private HttpServer server;
    private ApiEmbeddingService service;
    private volatile int statusToReturn = 200;
    private volatile int batchesServed;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String request = new String(body, StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            int inputs = mapper.readTree(request).path("input").size();
            StringBuilder data = new StringBuilder("[");
            for (int i = 0; i < inputs; i++) {
                if (i > 0) {
                    data.append(',');
                }
                // Distinct, non-parallel, order-dependent vectors so batching/ordering
                // survives L2 normalization: [i+1, 1] points in a different direction per input
                data.append("{\"object\":\"embedding\",\"index\":").append(i)
                        .append(",\"embedding\":[").append(i + 1).append(".0,1.0]}");
            }
            data.append(']');
            String response = "{\"object\":\"list\",\"data\":" + data + "}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusToReturn, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
            batchesServed++;
        });
        server.start();
        service = new ApiEmbeddingService(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key", "test-model");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void embedsTextAndNormalizesTheVector() {
        double[] vector = service.embed("photosynthesis");
        assertEquals(2, vector.length);
        // [1,1] normalizes to [1/√2, 1/√2]
        assertEquals(Math.sqrt(0.5), vector[0], 1e-9, "vector should be L2-normalized");
        assertEquals(Math.sqrt(0.5), vector[1], 1e-9);
    }

    @Test
    void cachesRepeatedTextsSoNoSecondCallHappens() {
        service.embed("photosynthesis");
        service.embed("photosynthesis");
        assertEquals(1, batchesServed, "second embed() must be served from the cache");
    }

    @Test
    void batchesMultipleTextsIntoOneCallPreservingOrder() {
        List<double[]> vectors = service.embedAll(List.of("alpha", "beta", "gamma"));
        assertEquals(3, vectors.size());
        // [1,1]→1/√2, [2,1]→2/√5, [3,1]→3/√10 — each input keeps its own direction
        assertEquals(1 / Math.sqrt(2), vectors.get(0)[0], 1e-9, "first input keeps its own vector");
        assertEquals(2 / Math.sqrt(5), vectors.get(1)[0], 1e-9, "second input keeps its own vector");
        assertEquals(3 / Math.sqrt(10), vectors.get(2)[0], 1e-9, "third input keeps its own vector");
        assertEquals(1, batchesServed, "three inputs fit in one batch");
    }

    @Test
    void splitsLargeBatchesAcrossCalls() {
        List<String> texts = new java.util.ArrayList<>();
        for (int i = 0; i < ApiEmbeddingService.MAX_BATCH_INPUTS + 5; i++) {
            texts.add("text-" + i);
        }
        List<double[]> vectors = service.embedAll(texts);
        assertEquals(texts.size(), vectors.size());
        assertEquals(2, batchesServed, "69 inputs need two batches at 64 per call");
    }

    @Test
    void blankTextYieldsAnEmptyVectorWithoutCallingTheApi() {
        assertEquals(0, service.embed("  ").length);
        assertEquals(0, batchesServed);
    }

    @Test
    void apiErrorsSurfaceAsIllegalState() {
        statusToReturn = 500;
        assertThrows(IllegalStateException.class, () -> service.embed("photosynthesis"));
    }

    @Test
    void longInputsAreTruncatedToTheCap() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            huge.append("word ");
        }
        double[] vector = service.embed(huge.toString());
        assertEquals(2, vector.length);
        assertTrue(huge.length() > ApiEmbeddingService.MAX_INPUT_CHARS, "input should have been truncated");
    }
}
