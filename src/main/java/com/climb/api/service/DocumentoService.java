package com.climb.api.service;

import com.climb.api.mapper.DocumentoMapper;
import com.climb.api.model.Documento;
import com.climb.api.model.Empresa;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.model.dto.DocumentoResponseDTO;
import com.climb.api.model.dto.DocumentoSolicitacaoRequestDTO;
import com.climb.api.model.dto.DocumentoUploadInfoResponseDTO;
import com.climb.api.model.dto.DocumentoValidacaoRequestDTO;
import com.climb.api.model.enums.DocumentoStatus;
import com.climb.api.repository.DocumentoRepository;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentoService {

    private static final int DIAS_EXPIRACAO_UPLOAD = 7;

    private final DocumentoRepository documentoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoMapper documentoMapper;
    private final CloudflareR2ArquivoStorageService arquivoStorageService;
    private final EmailService emailService;
    private final String frontendUrl;

    public DocumentoService(DocumentoRepository documentoRepository,
                            EmpresaRepository empresaRepository,
                            UsuarioRepository usuarioRepository,
                            DocumentoMapper documentoMapper,
                            CloudflareR2ArquivoStorageService arquivoStorageService,
                            EmailService emailService,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.documentoRepository = documentoRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.documentoMapper = documentoMapper;
        this.arquivoStorageService = arquivoStorageService;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    public List<DocumentoResponseDTO> listar() {
        return documentoMapper.toResponseDto(documentoRepository.findAll());
    }

    public List<DocumentoResponseDTO> listarPorEmpresa(Long empresaId) {
        return documentoMapper.toResponseDto(documentoRepository.findByEmpresa_IdEmpresa(empresaId));
    }

    public DocumentoResponseDTO buscarPorId(Long id) {
        Documento documento = buscarDocumento(id);
        return documentoMapper.toResponseDto(documento);
    }

    public DocumentoResponseDTO solicitar(DocumentoSolicitacaoRequestDTO dto, Long analistaId) {
        Empresa empresa = empresaRepository.findById(dto.empresaId())
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada: " + dto.empresaId()));
        Usuario analista = usuarioRepository.findById(analistaId)
                .orElseThrow(() -> new EntityNotFoundException("Analista não encontrado: " + analistaId));

        String titulo = exigirTexto(dto.titulo(), "O título do documento é obrigatório.");
        String emailDestinatario = normalizarEmail(dto.emailDestinatario());

        Documento documento = new Documento();
        documento.setEmpresa(empresa);
        documento.setAnalista(analista);
        documento.setTitulo(titulo);
        documento.setTipoDocumento(StringUtils.hasText(dto.tipoDocumento()) ? dto.tipoDocumento().trim() : titulo);
        documento.setEmailDestinatario(emailDestinatario);
        documento.setValidado(DocumentoStatus.PENDENTE);
        documento.setTokenUpload(gerarTokenUpload());
        documento.setTokenExpiraEm(LocalDateTime.now().plusDays(DIAS_EXPIRACAO_UPLOAD));
        documento.setDataSolicitacao(LocalDateTime.now());

        Documento salvo = documentoRepository.save(documento);
        enviarEmailSolicitacao(salvo);
        return documentoMapper.toResponseDto(salvo);
    }

    public DocumentoUploadInfoResponseDTO buscarSolicitacaoPorToken(String token) {
        Documento documento = buscarDocumentoPorTokenValido(token);
        return toUploadInfo(documento);
    }

    public DocumentoResponseDTO validar(Long id, DocumentoValidacaoRequestDTO dto) {
        Documento documento = buscarDocumento(id);
        validarStatusDeAnalise(documento, dto.validado());

        if (dto.validado() == DocumentoStatus.REPROVADO) {
            documento.setValidado(DocumentoStatus.PENDENTE);
            renovarLinkUpload(documento);
            Documento salvo = documentoRepository.save(documento);
            enviarEmailDocumentoReprovado(salvo);
            return documentoMapper.toResponseDto(salvo);
        }

        documento.setValidado(DocumentoStatus.APROVADO);
        documento.setTokenUpload(null);
        documento.setTokenExpiraEm(null);
        return documentoMapper.toResponseDto(documentoRepository.save(documento));
    }

    public DocumentoResponseDTO reenviarSolicitacao(Long id) {
        Documento documento = buscarDocumento(id);

        if (
                StringUtils.hasText(documento.getUrl()) &&
                documento.getValidado() != DocumentoStatus.PENDENTE &&
                documento.getValidado() != DocumentoStatus.REPROVADO
        ) {
            throw new IllegalArgumentException("Não é possível reenviar uma solicitação que já possui arquivo enviado.");
        }

        documento.setValidado(DocumentoStatus.PENDENTE);
        renovarLinkUpload(documento);

        Documento salvo = documentoRepository.save(documento);
        enviarEmailSolicitacao(salvo);
        return documentoMapper.toResponseDto(salvo);
    }

    public void deletar(Long id) {
        if (!documentoRepository.existsById(id)) {
            throw new EntityNotFoundException("Documento não encontrado: " + id);
        }
        documentoRepository.deleteById(id);
    }

    public DocumentoResponseDTO enviar(Long id, MultipartFile arquivo) {
        Documento documento = buscarDocumento(id);
        salvarArquivoNoDocumento(documento, arquivo);
        return documentoMapper.toResponseDto(documentoRepository.save(documento));
    }

    public DocumentoResponseDTO enviarPorToken(String token, MultipartFile arquivo) {
        Documento documento = buscarDocumentoPorTokenValido(token);
        salvarArquivoNoDocumento(documento, arquivo);
        documento.setTokenUpload(null);
        documento.setTokenExpiraEm(null);
        return documentoMapper.toResponseDto(documentoRepository.save(documento));
    }

    public String gerarUrlDownload(Long id) {
        Documento documento = buscarDocumento(id);
        if (!StringUtils.hasText(documento.getUrl())) {
            throw new IllegalArgumentException("Documento ainda não possui arquivo enviado.");
        }
        return arquivoStorageService.gerarUrlTemporariaDownload(documento.getUrl());
    }

    private Documento buscarDocumento(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado: " + id));
    }

    private Documento buscarDocumentoPorTokenValido(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Link de envio inválido.");
        }

        Documento documento = documentoRepository.findByTokenUpload(token.trim())
                .orElseThrow(() -> new EntityNotFoundException("Solicitação de documento não encontrada."));

        if (documento.getTokenExpiraEm() == null || documento.getTokenExpiraEm().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Link de envio expirado.");
        }

        return documento;
    }

    private void salvarArquivoNoDocumento(Documento documento, MultipartFile arquivo) {
        String prefixo = "documentos/empresa-%d".formatted(documento.getEmpresa().getIdEmpresa());
        ArquivoUploadResponseDTO upload = arquivoStorageService.salvar(arquivo, prefixo);
        documento.setUrl(upload.url());
        documento.setValidado(DocumentoStatus.EM_ANALISE);
        documento.setDataEnvio(LocalDateTime.now());
    }

    private String gerarTokenUpload() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void renovarLinkUpload(Documento documento) {
        documento.setEmailDestinatario(normalizarEmail(documento.getEmailDestinatario()));
        documento.setTokenUpload(gerarTokenUpload());
        documento.setTokenExpiraEm(LocalDateTime.now().plusDays(DIAS_EXPIRACAO_UPLOAD));
    }

    private void validarStatusDeAnalise(Documento documento, DocumentoStatus statusNovo) {
        if (statusNovo == null) {
            throw new IllegalArgumentException("O status é obrigatório.");
        }

        if (statusNovo != DocumentoStatus.APROVADO && statusNovo != DocumentoStatus.REPROVADO) {
            throw new IllegalArgumentException("Status inválido. Use APROVADO ou REPROVADO.");
        }

        if (!StringUtils.hasText(documento.getUrl())) {
            throw new IllegalArgumentException("O documento precisa ter um arquivo anexado antes da análise.");
        }

        if (documento.getValidado() == DocumentoStatus.APROVADO || documento.getValidado() == DocumentoStatus.REPROVADO) {
            throw new IllegalArgumentException("Este documento já foi analisado.");
        }

        if (documento.getValidado() != DocumentoStatus.EM_ANALISE) {
            throw new IllegalArgumentException("Somente documentos em análise podem ser aprovados ou reprovados.");
        }
    }

    private String montarLinkUpload(Documento documento) {
        return frontendUrl.replaceAll("/+$", "") + "/documentos/enviar/" + documento.getTokenUpload();
    }

    private void enviarEmailSolicitacao(Documento documento) {
        String nomeEmpresa = documento.getEmpresa() != null ? documento.getEmpresa().getNomeFantasia() : "sua empresa";
        String assunto = "Solicitação de documento - " + documento.getTitulo();
        String corpo = """
                Ola,

                A Climbe solicitou o envio do documento "%s" para %s.

                Acesse o link abaixo para anexar o arquivo:
                %s

                Este link expira em %d dias.
                """.formatted(documento.getTitulo(), nomeEmpresa, montarLinkUpload(documento), DIAS_EXPIRACAO_UPLOAD);

        emailService.enviarEmail(documento.getEmailDestinatario(), assunto, corpo);
    }

    private void enviarEmailDocumentoReprovado(Documento documento) {
        String nomeEmpresa = documento.getEmpresa() != null ? documento.getEmpresa().getNomeFantasia() : "sua empresa";
        String assunto = "Reenvio necessário - " + documento.getTitulo();
        String corpo = """
                Ola,

                O documento "%s" enviado para %s foi reprovado na análise.

                Para evitar uma nova solicitação, utilize o link abaixo para anexar uma versão corrigida:
                %s

                Este link expira em %d dias.
                """.formatted(documento.getTitulo(), nomeEmpresa, montarLinkUpload(documento), DIAS_EXPIRACAO_UPLOAD);

        emailService.enviarEmail(documento.getEmailDestinatario(), assunto, corpo);
    }

    private DocumentoUploadInfoResponseDTO toUploadInfo(Documento documento) {
        Empresa empresa = documento.getEmpresa();
        return new DocumentoUploadInfoResponseDTO(
                documento.getIdDocumento(),
                documento.getTitulo(),
                documento.getTipoDocumento(),
                empresa != null ? empresa.getIdEmpresa() : null,
                empresa != null ? empresa.getNomeFantasia() : null,
                documento.getTokenExpiraEm()
        );
    }

    private String exigirTexto(String valor, String mensagem) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }

    private String normalizarEmail(String email) {
        String normalizado = exigirTexto(email, "O e-mail do destinatário é obrigatório.").toLowerCase(Locale.ROOT);
        if (!normalizado.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Informe um e-mail válido para envio da solicitação.");
        }
        return normalizado;
    }
}
