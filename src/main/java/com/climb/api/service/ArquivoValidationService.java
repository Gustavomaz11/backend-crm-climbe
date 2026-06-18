package com.climb.api.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Service
public class ArquivoValidationService {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "gif", "bmp"
    );

    private static final Set<String> CONTENT_TYPES_PERMITIDOS = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp"
    );

    private final Tika tika = new Tika();

    public ArquivoValidado validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo está vazio.");
        }

        String nomeOriginal = sanitizarNome(arquivo.getOriginalFilename());
        String extensao = extrairExtensao(nomeOriginal);
        if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
            throw new IllegalArgumentException("Extensão de arquivo não permitida.");
        }

        try {
            byte[] conteudo = arquivo.getBytes();
            String contentType = tika.detect(conteudo, nomeOriginal);
            if (!CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
                throw new IllegalArgumentException("Tipo de arquivo não permitido: " + contentType);
            }

            validarIntegridade(contentType, conteudo);
            return new ArquivoValidado(nomeOriginal, contentType, conteudo.length, conteudo);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo: " + e.getMessage(), e);
        }
    }

    private void validarIntegridade(String contentType, byte[] conteudo) {
        switch (contentType) {
            case "application/pdf" -> validarPdf(conteudo);
            case "image/jpeg", "image/png", "image/gif", "image/bmp" -> validarImagem(conteudo);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> validarXlsx(conteudo);
            case "application/vnd.ms-excel" -> validarXls(conteudo);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> validarDocx(conteudo);
            case "application/msword" -> validarDoc(conteudo);
            default -> throw new IllegalArgumentException("Tipo de arquivo não permitido: " + contentType);
        }
    }

    private void validarPdf(byte[] conteudo) {
        try (PDDocument doc = Loader.loadPDF(conteudo)) {
            if (doc.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("PDF corrompido: sem páginas.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF corrompido: " + e.getMessage(), e);
        }
    }

    private void validarImagem(byte[] conteudo) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(conteudo));
            if (img == null) {
                throw new IllegalArgumentException("Imagem corrompida ou ilegível.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Imagem corrompida ou ilegível: " + e.getMessage(), e);
        }
    }

    private void validarXlsx(byte[] conteudo) {
        try (XSSFWorkbook ignored = new XSSFWorkbook(new ByteArrayInputStream(conteudo))) {
        } catch (Exception e) {
            throw new IllegalArgumentException("XLSX corrompido: " + e.getMessage(), e);
        }
    }

    private void validarXls(byte[] conteudo) {
        try (HSSFWorkbook ignored = new HSSFWorkbook(new ByteArrayInputStream(conteudo))) {
        } catch (Exception e) {
            throw new IllegalArgumentException("XLS corrompido: " + e.getMessage(), e);
        }
    }

    private void validarDocx(byte[] conteudo) {
        try (XWPFDocument ignored = new XWPFDocument(new ByteArrayInputStream(conteudo))) {
        } catch (Exception e) {
            throw new IllegalArgumentException("DOCX corrompido: " + e.getMessage(), e);
        }
    }

    private void validarDoc(byte[] conteudo) {
        try (POIFSFileSystem ignored = new POIFSFileSystem(new ByteArrayInputStream(conteudo))) {
        } catch (Exception e) {
            throw new IllegalArgumentException("DOC corrompido: " + e.getMessage(), e);
        }
    }

    private String extrairExtensao(String nomeArquivo) {
        int index = nomeArquivo.lastIndexOf('.');
        if (index < 0 || index == nomeArquivo.length() - 1) {
            return "";
        }
        return nomeArquivo.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizarNome(String nomeArquivo) {
        String nome = nomeArquivo == null || nomeArquivo.isBlank() ? "arquivo" : nomeArquivo;
        String normalizado = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalizado.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
