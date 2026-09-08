package com.aiteacher.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory {@link VectorStore}. Vectors are L2-normalized at
 * embedding time, so cosine similarity is a plain dot product. Ties are broken
 * by document order for deterministic results.
 */
@Component
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, List<Entry>> documents = new ConcurrentHashMap<>();

    private record Entry(DocumentChunk chunk, double[] vector) {
    }

    @Override
    public void put(String materialId, List<DocumentChunk> chunks, List<double[]> vectors) {
        List<Entry> entries = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            entries.add(new Entry(chunks.get(i), vectors.get(i)));
        }
        documents.put(materialId, entries);
    }

    @Override
    public List<ScoredChunk> search(String materialId, double[] queryVector, int topK) {
        List<Entry> entries = documents.getOrDefault(materialId, List.of());
        if (entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .map(e -> new ScoredChunk(e.chunk(), dot(e.vector(), queryVector)))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparingInt(s -> s.chunk().index()))
                .limit(topK)
                .toList();
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}