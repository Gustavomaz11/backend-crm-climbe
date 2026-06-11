package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.PropostaRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ContratoService {

    private final ContratoRepository repository;
    private final PropostaRepository propostaRepository;
    private final ContratoNotificacaoService contratoNotificacaoService;
    private final String diretorioContratosPdf;
    private final int diasAvisoVencimento;

    public ContratoService(ContratoRepository repository,
                           PropostaRepository propostaRepository,
                           ContratoNotificacaoService contratoNotificacaoService,
                           @Value("${app.contract-storage.directory:uploads/contratos}") String diretorioContratosPdf,
                           @Value("${app.contract-notifications.expiration-warning-days:30}") int diasAvisoVencimento) {
        this.repository = repository;
        this.propostaRepository = propostaRepository;
        this.contratoNotificacaoService = contratoNotificacaoService;
        this.diretorioContratosPdf = diretorioContratosPdf;
        this.diasAvisoVencimento = diasAvisoVencimento;
    }

    public List<Contrato> listar() {
        return repository.findAll();
    }

    public Contrato buscarPorId(Long id) {
        return repository.findById(id).orElseThrow();
    }

    public List<Contrato> listarPorStatus(String status) {
        return repository.findByStatus(status);
    }

    public Contrato criar(Contrato contrato) {
        contrato.setProposta(propostaRepository.findById(obterPropostaId(contrato))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta nao encontrada")));
        sincronizarCamposDaProposta(contrato);
        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoCriado(salvo);
        return salvo;
    }

    public Contrato atualizar(Long id, Contrato atualizado) {
        Contrato contrato = buscarPorId(id);
        Contrato anterior = snapshot(contrato);
        contrato.setDataInicio(atualizado.getDataInicio());
        contrato.setDataFim(atualizado.getDataFim());
        contrato.setStatus(atualizado.getStatus());
        sincronizarCamposDaProposta(contrato);
        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoAtualizado(anterior, salvo);
        return salvo;
    }

    public void deletar(Long id) {
        Contrato contrato = buscarPorId(id);
        contratoNotificacaoService.notificarContratoRemovido(contrato);
        repository.delete(contrato);
    }

    public Contrato enviarPdf(Long id, MultipartFile arquivo) {
        Contrato contrato = buscarPorId(id);
        contrato.setUrlPdf(salvarArquivoPdf(arquivo));
        return repository.save(contrato);
    }

    public ResponseEntity<Resource> baixarPdf(Long id) {
        Contrato contrato = buscarPorId(id);
        if (contrato.getUrlPdf() == null || contrato.getUrlPdf().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato sem PDF cadastrado");
        }

        try {
            Path arquivo = Paths.get(contrato.getUrlPdf());
            if (!Files.exists(arquivo)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF do contrato nao encontrado");
            }

            ByteArrayResource recurso = new ByteArrayResource(Files.readAllBytes(arquivo));
            String nomeArquivo = arquivo.getFileName().toString();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nomeArquivo + "\"")
                    .body(recurso);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao ler PDF do contrato", e);
        }
    }

    @Scheduled(cron = "${app.contract-notifications.expiration-cron:0 0 8 * * *}")
    public void notificarVencimentosProximos() {
        LocalDate hoje = LocalDate.now();
        LocalDate limite = hoje.plusDays(diasAvisoVencimento);
        repository.findByDataFimBetween(hoje, limite)
                .stream()
                .filter(contrato -> !statusIgnoradoParaVencimento(contrato.getStatus()))
                .forEach(contrato -> contratoNotificacaoService.notificarVencimentoProximo(contrato, hoje));
    }

    private boolean statusIgnoradoParaVencimento(String status) {
        if (status == null) {
            return false;
        }

        return Set.of("ENCERRADO", "CANCELADO", "INATIVO").contains(status.toUpperCase());
    }

    private Contrato snapshot(Contrato contrato) {
        Contrato snapshot = new Contrato();
        snapshot.setIdContrato(contrato.getIdContrato());
        snapshot.setProposta(contrato.getProposta());
        snapshot.setUsuario(contrato.getUsuario());
        snapshot.setEmpresa(contrato.getEmpresa());
        snapshot.setEmpresaNomeFantasia(contrato.getEmpresaNomeFantasia());
        snapshot.setDataInicio(contrato.getDataInicio());
        snapshot.setDataFim(contrato.getDataFim());
        snapshot.setUrlPdf(contrato.getUrlPdf());
        snapshot.setStatus(contrato.getStatus());
        return snapshot;
    }

    private void sincronizarCamposDaProposta(Contrato contrato) {
        if (contrato.getProposta() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposta e obrigatoria");
        }

        Long propostaId = contrato.getProposta().getIdProposta();
        var proposta = propostaRepository.findById(propostaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta nao encontrada"));

        contrato.setProposta(proposta);
        contrato.setUsuario(proposta.getUsuario());
        contrato.setEmpresa(proposta.getEmpresa());
        if (proposta.getEmpresa() != null) {
            contrato.setEmpresaNomeFantasia(proposta.getEmpresa().getNomeFantasia());
        }
    }

    private Long obterPropostaId(Contrato contrato) {
        if (contrato.getProposta() == null || contrato.getProposta().getIdProposta() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposta e obrigatoria");
        }

        return contrato.getProposta().getIdProposta();
    }

    private String salvarArquivoPdf(MultipartFile arquivo) {
        validarPdf(arquivo);

        try {
            Path pasta = Paths.get(diretorioContratosPdf);
            if (!Files.exists(pasta)) {
                Files.createDirectories(pasta);
            }

            String nomeArquivo = UUID.randomUUID() + "_" + normalizarNomeArquivo(arquivo.getOriginalFilename());
            Path destino = pasta.resolve(nomeArquivo);
            Files.copy(arquivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao salvar PDF do contrato", e);
        }
    }

    private void validarPdf(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo PDF vazio");
        }

        String contentType = arquivo.getContentType();
        if (contentType != null
                && !contentType.toLowerCase().startsWith("application/pdf")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "O contrato deve ser enviado em PDF");
        }

        try {
            byte[] conteudo = arquivo.getBytes();
            try (PDDocument documento = Loader.loadPDF(conteudo)) {
                if (documento.getNumberOfPages() == 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF corrompido: sem paginas");
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Arquivo invalido ou nao e um PDF", e);
        }
    }

    private String normalizarNomeArquivo(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "contrato.pdf";
        }

        String nomeLimpo = nomeOriginal.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!nomeLimpo.toLowerCase().endsWith(".pdf")) {
            nomeLimpo = nomeLimpo + ".pdf";
        }

        return nomeLimpo;
    }
}
