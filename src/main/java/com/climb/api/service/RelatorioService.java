package com.climb.api.service;

import com.climb.api.mapper.RelatorioMapper;
import com.climb.api.model.Contrato;
import com.climb.api.model.Relatorio;
import com.climb.api.model.dto.RelatorioPdfDownloadDTO;
import com.climb.api.model.dto.RelatorioRequestDTO;
import com.climb.api.model.dto.RelatorioResponseDTO;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.RelatorioRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;

@Service
public class RelatorioService {

    private final RelatorioRepository repository;
    private final ContratoRepository contratoRepository;
    private final Path pastaRelatorios;
    private final RelatorioMapper relatorioMapper;

    public RelatorioService(RelatorioRepository repository,
                            ContratoRepository contratoRepository,
                            RelatorioMapper relatorioMapper,
                            @Value("${app.reports.output-dir:uploads/relatorios}") String pastaRelatorios) {
        this.repository = repository;
        this.contratoRepository = contratoRepository;
        this.relatorioMapper = relatorioMapper;
        this.pastaRelatorios = Paths.get(pastaRelatorios);
    }

    public RelatorioResponseDTO uploadPdf(Long contratoId, String descricao, MultipartFile file) {
        byte[] conteudo = validarPdf(file);

        Relatorio relatorio = new Relatorio();
        relatorio.setContrato(buscarContratoPorId(contratoId));
        relatorio.setDescricao(descricao);
        relatorio.setDataEnvio(LocalDate.now());

        Relatorio relatorioSalvo = repository.save(relatorio);

        Path caminho = resolverCaminhoPdf(relatorioSalvo.getIdRelatorio());

        try {
            Files.createDirectories(caminho.getParent());

            Files.write(
                    caminho,
                    conteudo,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nao foi possivel salvar o PDF do relatorio"
            );
        }

        relatorioSalvo.setUrlPdf(caminho.toString());

        return relatorioMapper.toResponseDto(repository.save(relatorioSalvo));
    }

    public List<RelatorioResponseDTO> listar() {
        return relatorioMapper.toResponseDto(repository.findAll());
    }

    public List<RelatorioResponseDTO> listarPorContrato(Long contratoId) {
        return relatorioMapper.toResponseDto(
                repository.findByContrato_IdContrato(contratoId)
        );
    }

    public RelatorioResponseDTO buscarPorIdResponse(Long id) {
        Relatorio relatorio = buscarPorId(id);
        return relatorioMapper.toResponseDto(relatorio);
    }

    public RelatorioResponseDTO atualizar(Long id, RelatorioRequestDTO dto) {
        Relatorio relatorio = buscarPorId(id);

        if (dto.contratoId() != null) {
            relatorio.setContrato(buscarContratoPorId(dto.contratoId()));
        }

        if (dto.descricao() != null) {
            relatorio.setDescricao(dto.descricao());
        }

        return relatorioMapper.toResponseDto(repository.save(relatorio));
    }

    public void deletar(Long id) {
        Relatorio relatorio = buscarPorId(id);
        apagarPdfExistente(relatorio.getUrlPdf());
        repository.delete(relatorio);
    }

    public RelatorioPdfDownloadDTO obterPdfInline(Long id) {
        Relatorio relatorio = buscarPorId(id);
        validarPdfAnexado(relatorio);

        return new RelatorioPdfDownloadDTO(
                gerarNomeArquivo(relatorio, false),
                lerArquivoPdf(relatorio.getUrlPdf())
        );
    }

    public RelatorioPdfDownloadDTO obterPdfParaDownload(Long id) {
        Relatorio relatorio = buscarPorId(id);
        validarPdfAnexado(relatorio);

        return new RelatorioPdfDownloadDTO(
                gerarNomeArquivo(relatorio, true),
                lerArquivoPdf(relatorio.getUrlPdf())
        );
    }

    private Relatorio buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Relatorio nao encontrado"
                ));
    }

    private Contrato buscarContratoPorId(Long contratoId) {
        if (contratoId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contrato e obrigatorio"
            );
        }

        return contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Contrato nao encontrado"
                ));
    }

    private byte[] validarPdf(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Arquivo PDF e obrigatorio"
            );
        }

        try {
            byte[] conteudo = arquivo.getBytes();

            Tika tika = new Tika();
            String tipo = tika.detect(conteudo);

            if (!MediaType.APPLICATION_PDF_VALUE.equals(tipo)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Arquivo deve ser um PDF"
                );
            }

            try (PDDocument doc = Loader.loadPDF(conteudo)) {
                if (doc.getNumberOfPages() == 0) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "PDF invalido ou corrompido"
                    );
                }
            }

            return conteudo;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF invalido ou corrompido"
            );
        }
    }

    private void validarPdfAnexado(Relatorio relatorio) {
        if (!StringUtils.hasText(relatorio.getUrlPdf())
                || !Files.exists(Paths.get(relatorio.getUrlPdf()))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "PDF anexado nao encontrado para este relatorio"
            );
        }
    }

    private byte[] lerArquivoPdf(String caminhoPdf) {
        try {
            return Files.readAllBytes(Paths.get(caminhoPdf));
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nao foi possivel carregar o PDF do relatorio"
            );
        }
    }

    private Path resolverCaminhoPdf(Long relatorioId) {
        return pastaRelatorios.resolve("relatorio-" + relatorioId + ".pdf");
    }

    private String gerarNomeArquivo(Relatorio relatorio, boolean paraDownload) {
        String prefixo = paraDownload ? "relatorio-" : "preview-relatorio-";
        return prefixo + relatorio.getIdRelatorio() + ".pdf";
    }

    private void apagarPdfExistente(String caminhoPdf) {
        if (!StringUtils.hasText(caminhoPdf)) {
            return;
        }

        try {
            Files.deleteIfExists(Paths.get(caminhoPdf));
        } catch (IOException ignored) {
            // O relatorio e removido mesmo se a limpeza do arquivo falhar.
        }
    }
}