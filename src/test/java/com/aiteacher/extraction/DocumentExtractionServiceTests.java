package com.aiteacher.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Extraction tests with real files: a TXT written as UTF-8 bytes, PDFs and
 * presentations generated with PDFBox/POI (already on the classpath through
 * Tika). No sample documents are stored in the repository.
 */
class DocumentExtractionServiceTests {

	private final DocumentExtractionService service = new DocumentExtractionService(
			List.of(new PdfTextExtractor(), new TextFileExtractor(), new PowerPointTextExtractor()), 10 * 1024 * 1024);

	// ------------------------------------------------------------------
	// Real file content (different topics, none hardcoded in the app)
	// ------------------------------------------------------------------
	private static final String TXT_CONTENT = "Mangrove ecosystems store carbon at rates several times higher "
			+ "than terrestrial forests. This storage happens in the soil and in the biomass above it.";

	@Test
	void extractsTxtContentVerbatim() {
		MockMultipartFile file = new MockMultipartFile("file", "mangroves.txt", "text/plain",
				TXT_CONTENT.getBytes(StandardCharsets.UTF_8));

		ExtractedDocument document = service.extract(file);

		assertEquals("mangroves.txt", document.fileName());
		assertEquals(TXT_CONTENT, document.extractedText());
		assertEquals(TXT_CONTENT.length(), document.characterCount());
		assertNull(document.pageCount());
		assertEquals("text/plain", document.fileType());
	}

	@Test
	void extractsPdfTextAndPageCount() throws Exception {
		byte[] pdf = twoPagePdf(
				"Kepler's third law relates the orbital period to the semi-major axis.",
				"The square of the period is proportional to the cube of the axis.");

		ExtractedDocument document = service.extract(
				new MockMultipartFile("file", "kepler-notes.pdf", "application/pdf", pdf));

		assertTrue(document.extractedText().contains("Kepler's third law"));
		assertTrue(document.extractedText().contains("proportional to the cube"));
		assertEquals(2, document.pageCount());
		assertEquals("application/pdf", document.fileType());
	}

	@Test
	void extractsPptxTextAndSlideCount() throws Exception {
		byte[] pptx = twoSlidePptx(
				"Rust's ownership model prevents data races at compile time.",
				"Every value has a single owner that controls its lifetime.");

		ExtractedDocument document = service.extract(
				new MockMultipartFile("file", "rust-basics.pptx",
						"application/vnd.openxmlformats-officedocument.presentationml.presentation", pptx));

		assertTrue(document.extractedText().contains("ownership model"));
		assertTrue(document.extractedText().contains("single owner"));
		assertEquals(2, document.pageCount());
	}

	@Test
	void extractsLegacyPptTextAndSlideCount() throws Exception {
		byte[] ppt = twoSlidePpt(
				"Glycolysis splits glucose into two pyruvate molecules.",
				"ATP is produced through substrate-level phosphorylation.");

		ExtractedDocument document = service.extract(
				new MockMultipartFile("file", "glycolysis.ppt", "application/vnd.ms-powerpoint", ppt));

		assertTrue(document.extractedText().contains("Glycolysis"));
		assertTrue(document.extractedText().contains("pyruvate"));
		assertEquals(2, document.pageCount());
	}

	@Test
	void rejectsUnsupportedExtension() {
		MockMultipartFile file = new MockMultipartFile("file", "notes.docx", "application/octet-stream",
				"some bytes".getBytes(StandardCharsets.UTF_8));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.extract(file));
		assertEquals(DocumentExtractionService.UNSUPPORTED_MESSAGE, ex.getMessage());
	}

	@Test
	void rejectsExecutableBytesHiddenBehindPdfExtension() {
		// MZ header = Windows executable, even though the name claims PDF.
		byte[] exe = new byte[64];
		exe[0] = 'M';
		exe[1] = 'Z';
		MockMultipartFile file = new MockMultipartFile("file", "notes.pdf", "application/pdf", exe);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.extract(file));
		assertEquals(DocumentExtractionService.UNSUPPORTED_MESSAGE, ex.getMessage());
	}

	@Test
	void rejectsOversizedFile() {
		DocumentExtractionService tinyLimit = new DocumentExtractionService(
				List.of(new TextFileExtractor()), 100);

		MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain",
				new byte[1_000]);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> tinyLimit.extract(file));
		assertEquals(DocumentExtractionService.TOO_LARGE_MESSAGE, ex.getMessage());
	}

	@Test
	void rejectsEmptyFile() {
		MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.extract(file));
		assertEquals(DocumentExtractionService.EMPTY_MESSAGE, ex.getMessage());
	}

	@Test
	void rejectsPdfWithNoReadableText() throws Exception {
		byte[] blankPdf = singleBlankPagePdf();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.extract(new MockMultipartFile("file", "blank.pdf", "application/pdf", blankPdf)));
		assertEquals(DocumentExtractionService.NO_TEXT_MESSAGE, ex.getMessage());
	}

	// ------------------------------------------------------------------
	// Real file builders (PDFBox / POI, both already on the classpath)
	// ------------------------------------------------------------------
	private static byte[] twoPagePdf(String pageOne, String pageTwo) throws Exception {
		try (PDDocument document = new PDDocument()) {
			document.addPage(textPage(document, pageOne));
			document.addPage(textPage(document, pageTwo));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
	}

	private static PDPage textPage(PDDocument document, String text) throws Exception {
		PDPage page = new PDPage();			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
			stream.newLineAtOffset(50, 700);
			stream.showText(text);
			stream.endText();
		}
		return page;
	}

	private static byte[] singleBlankPagePdf() throws Exception {
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
	}

	private static byte[] twoSlidePptx(String slideOne, String slideTwo) throws Exception {
		try (XMLSlideShow show = new XMLSlideShow()) {
			XSLFSlide first = show.createSlide();
			XSLFTextBox boxOne = first.createTextBox();
			boxOne.setText(slideOne);
			XSLFSlide second = show.createSlide();
			XSLFTextBox boxTwo = second.createTextBox();
			boxTwo.setText(slideTwo);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			show.write(out);
			return out.toByteArray();
		}
	}

	private static byte[] twoSlidePpt(String slideOne, String slideTwo) throws Exception {
		try (HSLFSlideShow show = new HSLFSlideShow()) {
			HSLFSlide first = show.createSlide();
			HSLFTextBox boxOne = first.createTextBox();
			boxOne.setText(slideOne);
			HSLFSlide second = show.createSlide();
			HSLFTextBox boxTwo = second.createTextBox();
			boxTwo.setText(slideTwo);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			show.write(out);
			return out.toByteArray();
		}
	}
}