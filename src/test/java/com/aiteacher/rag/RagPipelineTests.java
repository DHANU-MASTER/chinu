package com.aiteacher.rag;	import static org.junit.jupiter.api.Assertions.assertEquals;
	import static org.junit.jupiter.api.Assertions.assertFalse;
	import static org.junit.jupiter.api.Assertions.assertNotNull;
	import static org.junit.jupiter.api.Assertions.assertNull;
	import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the RAG pipeline: chunking, deterministic embeddings and
 * similarity retrieval. All components are pure Java — no network, no AI key.
 */
class RagPipelineTests {

	private final ParagraphChunkingService chunking = new ParagraphChunkingService();
	private final LocalHashEmbeddingService embedding = new LocalHashEmbeddingService();
	private final RAGService rag = new RAGService();

	@Test
	void shortTextProducesSingleChunk() {
		String text = "Cirrus clouds form above 5,000 metres and are made of ice crystals.";
		List<DocumentChunk> chunks = chunking.chunk(text);
		assertEquals(1, chunks.size());
		assertEquals(text, chunks.get(0).text());
		assertEquals(0, chunks.get(0).index());
	}

	@Test
	void longTextSplitsIntoBoundedChunksPreservingContent() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) {
			sb.append("unique sentence number ").append(i)
					.append(" with distinctive marker ").append(i).append(". ");
		}
		String text = sb.toString();
		// Wrap in a paragraph with an explicit final marker.
		String full = text + " FINAL_END_MARKER";

		List<DocumentChunk> chunks = chunking.chunk(full);

		assertTrue(chunks.size() >= 3, "expected multiple chunks, got " + chunks.size());
		for (DocumentChunk chunk : chunks) {
			assertTrue(chunk.text().length() <= ParagraphChunkingService.MAX_CHUNK_CHARS,
					"chunk exceeds max size: " + chunk.text().length());
			assertFalse(chunk.text().isBlank());
		}
		// Content is preserved across chunks (nothing dropped).
		assertTrue(chunks.get(0).text().contains("unique sentence number 0"));
		String joined = String.join(" ", chunks.stream().map(DocumentChunk::text).toList());
		assertTrue(joined.contains("FINAL_END_MARKER"));
		// Consecutive chunks overlap so no concept is lost at a boundary.
		String fragment = chunks.get(0).text().substring(chunks.get(0).text().length() - 50);
		String head = chunks.get(1).text().substring(0, Math.min(300, chunks.get(1).text().length()));
		assertTrue(head.contains(fragment), "expected overlap between chunks");
	}

	@Test
	void blankTextProducesNoChunks() {
		assertTrue(chunking.chunk(null).isEmpty());
		assertTrue(chunking.chunk("   \n  ").isEmpty());
	}

	@Test
	void embeddingIsDeterministicNormalizedAndDistinct() {
		double[] a1 = embedding.embed("Photosynthesis uses chlorophyll to capture light");
		double[] a2 = embedding.embed("Photosynthesis uses chlorophyll to capture light");
		double[] b = embedding.embed("Binary search repeatedly halves a sorted array");

		assertNotNull(a1);
		assertEquals(a1.length, a2.length);
		assertEquals(LocalHashEmbeddingService.DIMENSIONS, a1.length);

		// Deterministic: same text, same vector.
		for (int i = 0; i < a1.length; i++) {
			assertEquals(a1[i], a2[i], 1e-12);
		}
		// Different topics land on different vectors.
		double distance = 0.0;
		for (int i = 0; i < a1.length; i++) {
			distance += (a1[i] - b[i]) * (a1[i] - b[i]);
		}
		assertTrue(Math.sqrt(distance) > 0.05, "vectors should differ across topics");

		// L2-normalized → cosine similarity is a plain dot product.
		double norm = 0.0;
		for (double v : a1) {
			norm += v * v;
		}
		assertEquals(1.0, Math.sqrt(norm), 1e-9);

		// Same-topic similarity beats cross-topic similarity.
		double sameTopic = dot(a1, a2);
		double crossTopic = dot(a1, b);
		assertTrue(sameTopic > crossTopic, "same-topic similarity should be higher");
	}

	@Test
	void retrievalFindsTheRelevantSectionOfAMixedDocument() {
		String material = section("Photosynthesis uses chlorophyll to capture light energy inside chloroplasts. ", 25)
				+ "\n\n"
				+ section("Binary search halves a sorted array by comparing the middle element with the target. ", 25)
				+ "\n\n"
				+ section("The Kumbh Mela is a major pilgrimage festival held on a rotating schedule in India. ", 25);

		List<DocumentChunk> hits = rag.retrieve(material,
				"how does chlorophyll capture light energy", 1);

		assertFalse(hits.isEmpty());
		String first = hits.get(0).text();
		assertTrue(first.contains("Photosynthesis"), "expected photosynthesis excerpt, got: " + first);
		assertTrue(first.contains("chlorophyll"));
		assertFalse(first.contains("middle element"), "top photosynthesis hit should not be from the binary search section");
		assertFalse(first.contains("Kumbh Mela"));

		List<DocumentChunk> algoHits = rag.retrieve(material,
				"binary search middle element sorted array target", 1);
		assertFalse(algoHits.isEmpty());
		assertTrue(algoHits.get(0).text().contains("halves a sorted array"),
				"expected binary search excerpt, got: " + algoHits.get(0).text());
	}

	/** Builds a section long enough to span its own chunks. */
	private static String section(String sentence, int repeats) {
		return sentence.repeat(repeats);
	}

	@Test
	void buildContextFormatsExcerptsAndHonorsCap() {
		String material = """
				First paragraph about photosynthesis and chlorophyll and light energy.

				Second paragraph about binary search and sorted arrays and midpoints.

				Third paragraph about historical events in ancient India.
				""";
		String context = rag.buildContext(material, "photosynthesis chlorophyll light", 2, 0);
		assertNotNull(context);
		assertTrue(context.startsWith("[Excerpt 1]"));
		assertTrue(context.contains("chlorophyll"));

		// Capped context never exceeds the limit.
		String capped = rag.buildContext(material, "photosynthesis chlorophyll light", 2, 80);
		assertNotNull(capped);
		assertTrue(capped.length() <= 80);

		// Nothing to ground on → null, never fake content.
		assertNull(rag.buildContext("   ", "anything", 2, 0));
		assertNull(rag.buildContext(null, "anything", 2, 0));
		assertTrue(rag.retrieve("", "anything", 2).isEmpty());
	}

	@Test
	void retrievalRanksRelevantChunksAboveUnrelatedOnes() {
		String material = section("Section alpha about the first concept with plenty of detail. ", 25)
				+ "\n\n"
				+ section("Section beta about a completely unrelated second concept. ", 25)
				+ "\n\n"
				+ section("Section gamma about the first concept again with more detail. ", 25);

		List<DocumentChunk> hits = rag.retrieve(material, "first concept detail", 3);
		assertEquals(3, hits.size());
		for (DocumentChunk hit : hits) {
			assertTrue(hit.text().contains("first concept"),
					"top hits should be from the relevant sections, got: " + hit.text());
			assertFalse(hit.text().contains("unrelated"));
		}
	}

	private static double dot(double[] a, double[] b) {
		double sum = 0.0;
		for (int i = 0; i < a.length; i++) {
			sum += a[i] * b[i];
		}
		return sum;
	}
}