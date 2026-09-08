package com.aiteacher.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real {@link EmbeddingService} backed by any OpenAI-compatible
 * {@code POST /embeddings} endpoint (OpenAI, Gemini's OpenAI-compat layer,
 * Azure, local servers such as Ollama/TEI, ...).
 *
 * <p>Configuration (environment variables, read once at construction):</p>
 * <pre>
 *   AI_API_KEY        — bearer token (default: empty, for local servers)
 *   AI_BASE_URL       — API base URL (default: https://api.openai.com/v1)
 *   AI_EMBEDDING_MODEL — embedding model (default: text-embedding-3-small)
 * </pre>
 *
 * <p>Behavioral notes:</p>
 * <ul>
 *   <li>Requests are batched (up to {@value #MAX_BATCH_INPUTS} inputs per call)
 *       and each input is capped at {@value #MAX_INPUT_CHARS} characters, so a
 *       whole document is indexed in a handful of round trips.</li>
 *   <li>Returned vectors are L2-normalized, so cosine similarity is a plain dot
 *       product — the same contract the {@link InMemoryVectorStore} and
 *       {@link PgVectorStore} rely on.</li>
 *   <li>Embeddings are memoized per text (documents are indexed once but searched
 *       many times; the cache also keeps query vectors stable across calls).</li>
 *   <li>Failures throw {@link IllegalStateException} — callers degrade by
 *       skipping retrieval rather than silently mixing two incompatible vector
 *       spaces (hash fallback vectors are meaningless next to model vectors).</li>
 * </ul>
 */
@Component
public class ApiEmbeddingService implements EmbeddingService {

    static final int MAX_BATCH_INPUTS = 64;
    static final int MAX_INPUT_CHARS = 8_000;

    private static final int CACHE_MAX_ENTRIES = 2_048;

    private final ObjectMapper json;
    private final HttpClient http;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    /** Simple bounded LRU: same text → same vector, avoids repeat API calls. */
    private final Map<String, double[]> cache =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
                    return size() > CACHE_MAX_ENTRIES;
                }
            };

    public ApiEmbeddingService(ObjectMapper json,
            @Value("${AI_BASE_URL:https://api.openai.com/v1}") String baseUrl,
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_EMBEDDING_MODEL:text-embedding-3-small}") String model) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "text-embedding-3-small" : model.trim();
        String base = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com/v1" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/embeddings";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public double[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new double[0];
        }
        String input = text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
        synchronized (cache) {
            double[] cached = cache.get(input);
            if (cached != null) {
                return cached;
            }
        }
        double[] vector = embedBatch(List.of(input)).get(0);
        synchronized (cache) {
            cache.put(input, vector);
        }
        return vector;
    }

    /**
     * Embeds many texts in as few API calls as possible, preserving input order.
     * Used by the vector store when indexing a whole document.
     */
    public List<double[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<double[]> result = new ArrayList<>(texts.size());
        List<String> pending = new ArrayList<>();
        List<Integer> pendingIndexes = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String input = text == null ? "" : (text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text);
            synchronized (cache) {
                double[] cached = cache.get(input);
                if (cached != null) {
                    result.add(cached);
                    continue;
                }
            }
            pending.add(input);
            pendingIndexes.add(i);
            result.add(null);
            if (pending.size() == MAX_BATCH_INPUTS) {
                fillFromApi(pending, pendingIndexes, result);
                pending = new ArrayList<>();
                pendingIndexes = new ArrayList<>();
            }
        }
        fillFromApi(pending, pendingIndexes, result);
        return result;
    }

    private void fillFromApi(List<String> inputs, List<Integer> indexes, List<double[]> result) {
        if (inputs.isEmpty()) {
            return;
        }
        List<double[]> vectors = embedBatch(inputs);
        for (int i = 0; i < inputs.size(); i++) {
            result.set(indexes.get(i), vectors.get(i));
            synchronized (cache) {
                cache.put(inputs.get(i), vectors.get(i));
            }
        }
    }

    private List<double[]> embedBatch(List<String> inputs) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "input", inputs);
        String body;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8));
            if (!apiKey.isEmpty()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Embedding API returned HTTP " + response.statusCode()
                        + " for model " + model);
            }
            body = response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Embedding API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding API call interrupted", e);
        }

        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Embedding API returned unparseable JSON", e);
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() != inputs.size()) {
            throw new IllegalStateException("Embedding API returned " + data.size()
                    + " vectors for " + inputs.size() + " inputs");
        }
        // The API may return items out of order; "index" restores input order.
        double[][] ordered = new double[inputs.size()][];
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new IllegalStateException("Embedding API returned an empty embedding");
            }
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= inputs.size()) {
                throw new IllegalStateException("Embedding API returned an out-of-range index: " + index);
            }
            double[] vector = new double[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).asDouble();
            }
            normalize(vector);
            ordered[index] = vector;
        }
        List<double[]> result = new ArrayList<>(inputs.size());
        for (double[] vector : ordered) {
            if (vector == null) {
                throw new IllegalStateException("Embedding API response is missing an input index");
            }
            result.add(vector);
        }
        return result;
    }

    private static void normalize(double[] vector) {
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        if (norm <= 0.0) {
            return;
        }
        double scale = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
