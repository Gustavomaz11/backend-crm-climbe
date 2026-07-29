package com.climb.api.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoPreviewServiceTest {
    private final DocumentoPreviewService service = new DocumentoPreviewService();

    @Test
    void deveRenderizarPaginaPdfComoPng() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }

        assertThat(service.totalPaginas(pdf, "application/pdf")).isEqualTo(1);
        assertThat(service.renderizarPagina(pdf, "application/pdf", 1))
                .startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void deveRenderizarSlidePptxComoPng() throws Exception {
        byte[] pptx;
        try (XMLSlideShow presentation = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            presentation.createSlide();
            presentation.write(output);
            pptx = output.toByteArray();
        }

        String contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        assertThat(service.totalPaginas(pptx, contentType)).isEqualTo(1);
        assertThat(service.renderizarPagina(pptx, contentType, 1))
                .startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }
}
