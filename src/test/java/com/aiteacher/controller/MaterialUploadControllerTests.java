package com.aiteacher.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.aiteacher.extraction.DocumentExtractionService;
import com.aiteacher.extraction.PdfTextExtractor;
import com.aiteacher.extraction.PowerPointTextExtractor;
import com.aiteacher.extraction.TextFileExtractor;

/**
 * Web tests for POST /api/material/upload. Real files are uploaded through the
 * multipart endpoint: a TXT with actual content, a generated PDF, a generated
 * PPTX — plus the rejection cases (unsupported, empty, executable spoof).
 */
@WebMvcTest(MaterialUploadController.class)
@Import({ DocumentExtractionService.class, PdfTextExtractor.class, TextFileExtractor.class,
		PowerPointTextExtractor.class })
class MaterialUploadControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void uploadsTxtAndReturnsRealExtractedContent() throws Exception {
		String content = "Hydrogen fuel cells combine hydrogen and oxygen to produce electricity and water.";

		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "fuel-cells.txt", "text/plain",
								content.getBytes(StandardCharsets.UTF_8))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("fuel-cells.txt"))
				.andExpect(jsonPath("$.fileType").value("text/plain"))
				.andExpect(jsonPath("$.characterCount").value(content.length()))
				.andExpect(jsonPath("$.extractedText").value(content));
	}

	@Test
	void uploadsPdfAndReturnsPageCountAndText() throws Exception {
		byte[] pdf = twoPagePdf("Correlation does not imply causation.",
				"Randomised trials isolate causal effects.");

		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "statistics.pdf", "application/pdf", pdf)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("statistics.pdf"))
				.andExpect(jsonPath("$.pageCount").value(2))
				.andExpect(jsonPath("$.extractedText").value(
						org.hamcrest.Matchers.containsString("Randomised trials")));
	}

	@Test
	void uploadsPptxAndReturnsSlideCountAndText() throws Exception {
		byte[] pptx = twoSlidePptx("Latent heat is absorbed during a phase change.",
				"Evaporation cools the remaining liquid.");

		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "thermodynamics.pptx",
								"application/vnd.openxmlformats-officedocument.presentationml.presentation", pptx)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pageCount").value(2))
				.andExpect(jsonPath("$.extractedText").value(
						org.hamcrest.Matchers.containsString("Latent heat")));
	}

	@Test
	void rejectsUnsupportedFileType() throws Exception {
		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "notes.docx", "application/octet-stream",
								"not supported".getBytes(StandardCharsets.UTF_8))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(DocumentExtractionService.UNSUPPORTED_MESSAGE));
	}

	@Test
	void rejectsEmptyFile() throws Exception {
		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(DocumentExtractionService.EMPTY_MESSAGE));
	}

	@Test
	void rejectsExecutableBytesHiddenBehindPdfExtension() throws Exception {
		byte[] exe = new byte[64];
		exe[0] = 'M';
		exe[1] = 'Z';

		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "notes.pdf", "application/pdf", exe)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(DocumentExtractionService.UNSUPPORTED_MESSAGE));
	}

	@Test
	void rejectsFileWithNoReadableText() throws Exception {
		byte[] blankPdf;
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			blankPdf = out.toByteArray();
		}

		mockMvc.perform(multipart("/api/material/upload")
						.file(new MockMultipartFile("file", "blank.pdf", "application/pdf", blankPdf)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(DocumentExtractionService.NO_TEXT_MESSAGE));
	}

	// ------------------------------------------------------------------
	// Real file builders
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
		PDPage page = new PDPage();
		try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
			stream.beginText();
			stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
			stream.newLineAtOffset(50, 700);
			stream.showText(text);
			stream.endText();
		}
		return page;
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
}