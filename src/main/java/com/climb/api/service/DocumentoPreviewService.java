package com.climb.api.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentoPreviewService {
    private static final Semaphore RENDERIZACOES = new Semaphore(2, true);
    private static final Set<String> EXTENSOES_REVISAVEIS = Set.of("pdf", "ppt", "pptx");
    private static final String PDF = "application/pdf";
    private static final String PPT = "application/vnd.ms-powerpoint";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    public void validarArquivoRevisavel(MultipartFile arquivo) {
        String nome = arquivo == null ? null : arquivo.getOriginalFilename();
        String extensao = extensao(nome);
        if (!EXTENSOES_REVISAVEIS.contains(extensao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use um arquivo PDF, PPT ou PPTX para permitir a revisão do cliente");
        }
    }

    public void validarContentType(String contentType) {
        if (!Set.of(PDF, PPT, PPTX).contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O formato enviado não pode ser exibido para revisão. Use PDF, PPT ou PPTX");
        }
    }

    public int totalPaginas(byte[] conteudo, String contentType) {
        try {
            return switch (contentType) {
                case PDF -> totalPaginasPdf(conteudo);
                case PPTX -> totalSlidesPptx(conteudo);
                case PPT -> totalSlidesPpt(conteudo);
                default -> throw tipoNaoSuportado();
            };
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível gerar a visualização do documento", e);
        }
    }

    public byte[] renderizarPagina(byte[] conteudo, String contentType, int pagina) {
        if (pagina < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida");
        }
        boolean adquirido = false;
        try {
            adquirido = RENDERIZACOES.tryAcquire(30, TimeUnit.SECONDS);
            if (!adquirido) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "A visualização está ocupada. Tente novamente em alguns segundos");
            }
            return switch (contentType) {
                case PDF -> renderizarPdf(conteudo, pagina);
                case PPTX -> renderizarPptx(conteudo, pagina);
                case PPT -> renderizarPpt(conteudo, pagina);
                default -> throw tipoNaoSuportado();
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A renderização foi interrompida", e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível renderizar a página solicitada", e);
        } finally {
            if (adquirido) RENDERIZACOES.release();
        }
    }

    private int totalPaginasPdf(byte[] conteudo) throws Exception {
        try (PDDocument document = Loader.loadPDF(conteudo)) {
            return document.getNumberOfPages();
        }
    }

    private byte[] renderizarPdf(byte[] conteudo, int pagina) throws Exception {
        try (PDDocument document = Loader.loadPDF(conteudo)) {
            validarPagina(pagina, document.getNumberOfPages());
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pagina - 1, 130, ImageType.RGB);
            return png(image);
        }
    }

    private int totalSlidesPptx(byte[] conteudo) throws Exception {
        try (XMLSlideShow slides = new XMLSlideShow(new ByteArrayInputStream(conteudo))) {
            return slides.getSlides().size();
        }
    }

    private byte[] renderizarPptx(byte[] conteudo, int pagina) throws Exception {
        try (XMLSlideShow slides = new XMLSlideShow(new ByteArrayInputStream(conteudo))) {
            validarPagina(pagina, slides.getSlides().size());
            return renderizarSlide(slides.getPageSize(), slides.getSlides().get(pagina - 1));
        }
    }

    private int totalSlidesPpt(byte[] conteudo) throws Exception {
        try (HSLFSlideShow slides = new HSLFSlideShow(new ByteArrayInputStream(conteudo))) {
            return slides.getSlides().size();
        }
    }

    private byte[] renderizarPpt(byte[] conteudo, int pagina) throws Exception {
        try (HSLFSlideShow slides = new HSLFSlideShow(new ByteArrayInputStream(conteudo))) {
            validarPagina(pagina, slides.getSlides().size());
            return renderizarSlide(slides.getPageSize(), slides.getSlides().get(pagina - 1));
        }
    }

    private byte[] renderizarSlide(Dimension tamanho, Slide<?, ?> slide) throws Exception {
        double escala = Math.min(1.8, 1400d / Math.max(1, tamanho.width));
        int largura = Math.max(1, (int) Math.round(tamanho.width * escala));
        int altura = Math.max(1, (int) Math.round(tamanho.height * escala));
        BufferedImage image = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setPaint(Color.WHITE);
            graphics.fillRect(0, 0, largura, altura);
            graphics.scale(escala, escala);
            slide.draw(graphics);
        } finally {
            graphics.dispose();
        }
        return png(image);
    }

    private byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void validarPagina(int pagina, int total) {
        if (pagina > total) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Página não encontrada");
        }
    }

    private ResponseStatusException tipoNaoSuportado() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de documento não suportado");
    }

    private String extensao(String nome) {
        if (nome == null || !nome.contains(".")) return "";
        return nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
