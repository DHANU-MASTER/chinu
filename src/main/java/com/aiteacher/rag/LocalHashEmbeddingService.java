package com.aiteacher.rag;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Deterministic, offline embedding: no external API, no model download, works
 * for any language including Hindi and Kannada. Text is tokenized into word
 * tokens plus character n-grams (covers scripts and spelling variants), each
 * token is hashed onto two dimensions of a fixed-size vector (bucket + sign),
 * and the vector is L2-normalized. Similarity between two texts is their
 * cosine similarity, computed as a plain dot product of the normalized vectors.
 *
 * <p>This is a hackathon-grade lexical/semantic-hybrid embedding — it captures
 * topical overlap well enough for retrieval but is not a learned model.</p>
 */
@Component
public class LocalHashEmbeddingService implements EmbeddingService {

    static final int DIMENSIONS = 256;

    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long OFFSET_A = 0xcbf29ce484222325L;
    private static final long OFFSET_B = 0x9e3779b97f4a7c15L;

    /** Word tokens: sequences of letters and digits. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String lower = text.toLowerCase(Locale.ROOT);

        Matcher matcher = WORD.matcher(lower);
        while (matcher.find()) {
            addToken(vector, matcher.group(), 1.0);
        }
        addNgrams(vector, lower, 2, 0.5);
        addNgrams(vector, lower, 3, 0.25);

        normalize(vector);
        return vector;
    }

    private static void addNgrams(double[] vector, String text, int n, double weight) {
        if (text.length() < n) {
            return;
        }
        for (int i = 0; i <= text.length() - n; i++) {
            addToken(vector, text.substring(i, i + n), weight);
        }
    }

    private static void addToken(double[] vector, String token, double weight) {
        long h1 = fnv1a(token, OFFSET_A);
        long h2 = fnv1a(token, OFFSET_B);
        int dim = Math.floorMod(h1, DIMENSIONS);
        double sign = (h2 & 1L) == 0L ? 1.0 : -1.0;
        vector[dim] += sign * weight;
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

    private static long fnv1a(String s, long offsetBasis) {
        long hash = offsetBasis;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}