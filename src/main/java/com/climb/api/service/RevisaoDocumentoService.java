package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.enums.RevisaoDocumentoStatus;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import com.climb.api.repository.*;
import com.climb.api.config.ZapSignProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RevisaoDocumentoService {
    private static final Logger log = LoggerFactory.getLogger(RevisaoDocumentoService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATA_EMAIL = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final RevisaoDocumentoRepository revisaoRepository;
    private final RevisaoDocumentoVersaoRepository versaoRepository;
    private final RevisaoDocumentoAnotacaoRepository anotacaoRepository;
    private final PropostaRepository propostaRepository;
    private final ContratoRepository contratoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CloudflareR2ArquivoStorageService storageService;
    private final DocumentoPreviewService previewService;
    private final EmailService emailService;
    private final RbacService rbacService;
    private final ZapSignClient zapSignClient;
    private final ZapSignProperties zapSignProperties;
    private final String frontendUrl;
    private final int diasExpiracao;

    public RevisaoDocumentoService(RevisaoDocumentoRepository revisaoRepository,
                                   RevisaoDocumentoVersaoRepository versaoRepository,
                                   RevisaoDocumentoAnotacaoRepository anotacaoRepository,
                                   PropostaRepository propostaRepository,
                                   ContratoRepository contratoRepository,
                                   UsuarioRepository usuarioRepository,
                                   CloudflareR2ArquivoStorageService storageService,
                                   DocumentoPreviewService previewService,
                                   EmailService emailService,
                                   RbacService rbacService,
                                   ZapSignClient zapSignClient,
                                   ZapSignProperties zapSignProperties,
                                   @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                   @Value("${app.document-review.expiration-days:30}") int diasExpiracao) {
        this.revisaoRepository = revisaoRepository;
        this.versaoRepository = versaoRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.propostaRepository = propostaRepository;
        this.contratoRepository = contratoRepository;
        this.usuarioRepository = usuarioRepository;
        this.storageService = storageService;
        this.previewService = previewService;
        this.emailService = emailService;
        this.rbacService = rbacService;
        this.zapSignClient = zapSignClient;
        this.zapSignProperties = zapSignProperties;
        this.frontendUrl = frontendUrl;
        this.diasExpiracao = diasExpiracao;
    }

    public void validarEnvio(Empresa empresa, MultipartFile arquivo) {
        if (empresa == null || !StringUtils.hasText(empresa.getEmail()) || !empresa.getEmail().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cadastre um e-mail válido para a empresa antes de enviar o documento ao cliente");
        }
        previewService.validarArquivoRevisavel(arquivo);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO iniciarProposta(Proposta proposta,
                                                       ArquivoUploadResponseDTO arquivo,
                                                       Usuario criadoPor) {
        return iniciar(RevisaoDocumentoTipo.PROPOSTA, proposta.getIdProposta(), proposta.getEmpresa(), arquivo, criadoPor);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO iniciarContrato(Contrato contrato,
                                                       ArquivoUploadResponseDTO arquivo,
                                                       Usuario criadoPor) {
        return iniciar(RevisaoDocumentoTipo.CONTRATO, contrato.getIdContrato(), contrato.getEmpresa(), arquivo, criadoPor);
    }

    private RevisaoDocumentoResponseDTO iniciar(RevisaoDocumentoTipo tipo,
                                                Long referenciaId,
                                                Empresa empresa,
                                                ArquivoUploadResponseDTO arquivo,
                                                Usuario criadoPor) {
        previewService.validarContentType(arquivo.contentType());
        RevisaoDocumento revisao = new RevisaoDocumento();
        revisao.setTipo(tipo);
        revisao.setReferenciaId(referenciaId);
        revisao.setEmpresa(empresa);
        revisao.setEmpresaNome(empresa.getNomeFantasia());
        revisao.setDestinatarioEmail(empresa.getEmail().trim());
        revisao.setDestinatarioNome(empresa.getRepresentanteNome());
        revisao.setVersaoAtual(1);
        revisao.setStatus(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE);
        revisao.setEmailStatus("PENDENTE");
        revisao.setCriadoEm(LocalDateTime.now());
        renovarAcesso(revisao);
        revisao = revisaoRepository.save(revisao);

        criarVersao(revisao, 1, arquivo, criadoPor);
        enviarAoCliente(revisao, false);
        return toResponse(revisao);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO buscarPublica(String token) {
        RevisaoDocumento revisao = buscarTokenValido(token);
        sincronizarAssinaturaSilenciosamente(revisao);
        return toResponse(revisao);
    }

    @Transactional(readOnly = true)
    public RevisaoDocumentoResponseDTO buscarInterna(RevisaoDocumentoTipo tipo, Long referenciaId) {
        RevisaoDocumento revisao = revisaoRepository.findByTipoAndReferenciaId(tipo, referenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nenhuma revisão foi enviada para este documento"));
        return toResponse(revisao);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO enviarParaRevisao(String token, RevisaoClienteRequestDTO request) {
        RevisaoDocumento revisao = buscarTokenValido(token);
        garantirAguardandoCliente(revisao);
        RevisaoDocumentoVersao versao = versaoAtual(revisao);
        garantirAssinaturaNaoIniciada(revisao, versao);
        int totalPaginas = totalPaginas(versao);

        for (RevisaoAnotacaoRequestDTO item : request.anotacoes()) {
            validarAnotacao(item, totalPaginas);
            RevisaoDocumentoAnotacao anotacao = new RevisaoDocumentoAnotacao();
            anotacao.setVersao(versao);
            anotacao.setPagina(item.pagina());
            anotacao.setPosicaoX(item.x());
            anotacao.setPosicaoY(item.y());
            anotacao.setLargura(item.largura());
            anotacao.setAltura(item.altura());
            anotacao.setCor(StringUtils.hasText(item.cor()) ? item.cor() : "#FACC15");
            anotacao.setComentario(item.comentario().trim());
            anotacao.setCriadoEm(LocalDateTime.now());
            anotacaoRepository.save(anotacao);
        }

        concluirResposta(revisao, versao, RevisaoDocumentoStatus.AJUSTES_SOLICITADOS,
                request.comentarioGeral(), null);
        atualizarStatusDominio(revisao, RevisaoDocumentoStatus.AJUSTES_SOLICITADOS);
        notificarEquipe(revisao, "Cliente solicitou ajustes",
                "O cliente marcou trechos e enviou comentários na versão " + revisao.getVersaoAtual() + ".");
        return toResponse(revisao);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO aprovar(String token) {
        RevisaoDocumento revisao = buscarTokenValido(token);
        garantirAguardandoCliente(revisao);
        RevisaoDocumentoVersao versao = versaoAtual(revisao);

        if (revisao.getTipo() == RevisaoDocumentoTipo.CONTRATO) {
            return iniciarAssinaturaContrato(revisao, versao);
        }

        concluirResposta(revisao, versao, RevisaoDocumentoStatus.APROVADO, null, null);
        atualizarStatusDominio(revisao, RevisaoDocumentoStatus.APROVADO);
        notificarEquipe(revisao, documentoNome(revisao) + " aprovado pelo cliente",
                "O cliente aprovou a versão " + revisao.getVersaoAtual() + " do documento.");
        return toResponse(revisao);
    }

    @Transactional
    public void processarWebhookZapSign(String secret, ZapSignWebhookRequestDTO request) {
        validarSegredoWebhook(secret);
        if (request == null || !"doc_signed".equalsIgnoreCase(request.eventType())) {
            return;
        }
        if (!StringUtils.hasText(request.token())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token do documento ZapSign não informado");
        }

        RevisaoDocumentoVersao versao = versaoRepository.findByZapsignDocumentoToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Documento ZapSign não vinculado a uma revisão"));
        sincronizarAssinatura(versao, true);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO reprovar(String token, RevisaoReprovacaoRequestDTO request) {
        RevisaoDocumento revisao = buscarTokenValido(token);
        garantirAguardandoCliente(revisao);
        RevisaoDocumentoVersao versao = versaoAtual(revisao);
        garantirAssinaturaNaoIniciada(revisao, versao);
        String justificativa = request.justificativa().trim();
        concluirResposta(revisao, versao, RevisaoDocumentoStatus.REPROVADO, null, justificativa);
        atualizarStatusDominio(revisao, RevisaoDocumentoStatus.REPROVADO);
        notificarEquipe(revisao, documentoNome(revisao) + " reprovado pelo cliente",
                "Justificativa do cliente: " + justificativa);
        return toResponse(revisao);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO novaVersao(Long revisaoId, Long usuarioId, MultipartFile arquivo) {
        RevisaoDocumento revisao = buscarPorId(revisaoId);
        validarPermissao(revisao, usuarioId);
        previewService.validarArquivoRevisavel(arquivo);
        ArquivoUploadResponseDTO upload = storageService.salvar(arquivo,
                "revisoes/" + revisao.getTipo().name().toLowerCase(Locale.ROOT) + "-" + revisao.getReferenciaId());
        previewService.validarContentType(upload.contentType());

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        int numero = revisao.getVersaoAtual() + 1;
        revisao.setVersaoAtual(numero);
        revisao.setStatus(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE);
        revisao.setJustificativa(null);
        revisao.setRespondidoEm(null);
        atualizarDestinatario(revisao);
        renovarAcesso(revisao);
        criarVersao(revisao, numero, upload, usuario);
        atualizarArquivoDominio(revisao, upload.url());
        enviarAoCliente(revisao, true);
        return toResponse(revisao);
    }

    @Transactional
    public RevisaoDocumentoResponseDTO reenviar(Long revisaoId, Long usuarioId) {
        RevisaoDocumento revisao = buscarPorId(revisaoId);
        validarPermissao(revisao, usuarioId);
        atualizarDestinatario(revisao);
        renovarAcesso(revisao);
        enviarAoCliente(revisao, revisao.getVersaoAtual() > 1);
        return toResponse(revisao);
    }

    @Transactional(readOnly = true)
    public byte[] paginaPublica(String token, int pagina) {
        return renderizarPagina(buscarTokenValido(token), pagina);
    }

    @Transactional(readOnly = true)
    public byte[] paginaInterna(Long revisaoId, int versaoNumero, int pagina) {
        RevisaoDocumento revisao = buscarPorId(revisaoId);
        RevisaoDocumentoVersao versao = versaoRepository.findByRevisaoIdAndNumero(revisao.getId(), versaoNumero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versão não encontrada"));
        return previewService.renderizarPagina(storageService.baixar(versao.getArquivoUrl()), versao.getContentType(), pagina);
    }

    private byte[] renderizarPagina(RevisaoDocumento revisao, int pagina) {
        RevisaoDocumentoVersao versao = versaoAtual(revisao);
        return previewService.renderizarPagina(storageService.baixar(versao.getArquivoUrl()), versao.getContentType(), pagina);
    }

    private void criarVersao(RevisaoDocumento revisao,
                             int numero,
                             ArquivoUploadResponseDTO arquivo,
                             Usuario criadoPor) {
        RevisaoDocumentoVersao versao = new RevisaoDocumentoVersao();
        versao.setRevisao(revisao);
        versao.setNumero(numero);
        versao.setArquivoUrl(arquivo.url());
        versao.setNomeArquivo(arquivo.nomeOriginal());
        versao.setContentType(arquivo.contentType());
        versao.setTotalPaginas(previewService.totalPaginas(storageService.baixar(arquivo.url()), arquivo.contentType()));
        versao.setResultado(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE);
        versao.setCriadoPor(criadoPor);
        versao.setCriadoEm(LocalDateTime.now());
        versaoRepository.save(versao);
    }

    private void concluirResposta(RevisaoDocumento revisao,
                                  RevisaoDocumentoVersao versao,
                                  RevisaoDocumentoStatus status,
                                  String comentarioGeral,
                                  String justificativa) {
        LocalDateTime agora = LocalDateTime.now();
        versao.setResultado(status);
        versao.setComentarioGeral(limparOpcional(comentarioGeral));
        versao.setJustificativa(limparOpcional(justificativa));
        versao.setRespondidoEm(agora);
        versaoRepository.save(versao);
        revisao.setStatus(status);
        revisao.setJustificativa(limparOpcional(justificativa));
        revisao.setRespondidoEm(agora);
        revisao.setAtualizadoEm(agora);
        revisaoRepository.save(revisao);
    }

    private void atualizarStatusDominio(RevisaoDocumento revisao, RevisaoDocumentoStatus status) {
        if (revisao.getTipo() == RevisaoDocumentoTipo.PROPOSTA) {
            Proposta proposta = propostaRepository.findById(revisao.getReferenciaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta não encontrada"));
            proposta.setStatus(status == RevisaoDocumentoStatus.APROVADO ? PropostaStatus.APROVADA
                    : status == RevisaoDocumentoStatus.REPROVADO ? PropostaStatus.REJEITADA : PropostaStatus.PENDENTE);
            propostaRepository.save(proposta);
        } else {
            Contrato contrato = contratoRepository.findById(revisao.getReferenciaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado"));
            contrato.setStatus(status == RevisaoDocumentoStatus.APROVADO ? ContratoService.STATUS_APROVADO
                    : status == RevisaoDocumentoStatus.REPROVADO ? ContratoService.STATUS_REJEITADO : ContratoService.STATUS_PENDENTE);
            contratoRepository.save(contrato);
        }
    }

    private void atualizarArquivoDominio(RevisaoDocumento revisao, String url) {
        atualizarStatusDominio(revisao, RevisaoDocumentoStatus.AGUARDANDO_CLIENTE);
        if (revisao.getTipo() == RevisaoDocumentoTipo.PROPOSTA) {
            Proposta proposta = propostaRepository.findById(revisao.getReferenciaId()).orElseThrow();
            proposta.setUrl(url);
            propostaRepository.save(proposta);
        } else {
            Contrato contrato = contratoRepository.findById(revisao.getReferenciaId()).orElseThrow();
            contrato.setUrlPdf(url);
            contratoRepository.save(contrato);
        }
    }

    private RevisaoDocumentoResponseDTO iniciarAssinaturaContrato(RevisaoDocumento revisao,
                                                                   RevisaoDocumentoVersao versao) {
        if (StringUtils.hasText(versao.getZapsignDocumentoToken())) {
            return toResponse(revisao);
        }
        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(versao.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A assinatura pela ZapSign exige que o contrato esteja em formato PDF");
        }

        String nomeSignatario = StringUtils.hasText(revisao.getDestinatarioNome())
                ? revisao.getDestinatarioNome().trim()
                : revisao.getEmpresaNome();
        String cpf = revisao.getEmpresa() == null ? null : revisao.getEmpresa().getRepresentanteCpf();
        if (StringUtils.hasText(cpf) && cpf.replaceAll("\\D", "").length() != 11) {
            cpf = null;
        }
        String redirectLink = frontendUrl.replaceAll("/+$", "") + "/revisao/" + revisao.getToken()
                + "?assinatura=concluida";
        String externalId = "climbe-revisao-%d-versao-%d".formatted(revisao.getId(), versao.getNumero());

        ZapSignClient.Documento documento = zapSignClient.criarDocumento(
                versao.getNomeArquivo(),
                storageService.baixar(versao.getArquivoUrl()),
                nomeSignatario,
                revisao.getDestinatarioEmail(),
                cpf,
                redirectLink,
                externalId
        );
        String signatarioToken = documento.primeiroSignatarioToken();
        if (!StringUtils.hasText(signatarioToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A ZapSign não retornou o link do signatário");
        }

        versao.setZapsignDocumentoToken(documento.token());
        versao.setZapsignSignatarioToken(signatarioToken);
        versao.setZapsignStatus(documento.status());
        versao.setZapsignCriadoEm(LocalDateTime.now());
        versaoRepository.save(versao);
        return toResponse(revisao);
    }

    private void sincronizarAssinaturaSilenciosamente(RevisaoDocumento revisao) {
        if (revisao.getTipo() != RevisaoDocumentoTipo.CONTRATO
                || revisao.getStatus() != RevisaoDocumentoStatus.AGUARDANDO_CLIENTE) {
            return;
        }
        RevisaoDocumentoVersao versao = versaoAtual(revisao);
        if (!StringUtils.hasText(versao.getZapsignDocumentoToken())) {
            return;
        }
        try {
            sincronizarAssinatura(versao, false);
        } catch (ResponseStatusException exception) {
            log.warn("Não foi possível sincronizar a assinatura ZapSign da revisão {}", revisao.getId());
        }
    }

    private void sincronizarAssinatura(RevisaoDocumentoVersao versao, boolean exigirAssinado) {
        ZapSignClient.Documento documento = zapSignClient.detalharDocumento(versao.getZapsignDocumentoToken());
        versao.setZapsignStatus(documento.status());
        versaoRepository.save(versao);

        if (!documento.assinado()) {
            if (exigirAssinado) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A ZapSign ainda não confirmou a assinatura completa do documento");
            }
            return;
        }

        RevisaoDocumento revisao = versao.getRevisao();
        if (revisao.getStatus() == RevisaoDocumentoStatus.APROVADO) {
            if (versao.getZapsignAssinadoEm() == null) {
                versao.setZapsignAssinadoEm(LocalDateTime.now());
                versaoRepository.save(versao);
            }
            return;
        }
        if (revisao.getStatus() != RevisaoDocumentoStatus.AGUARDANDO_CLIENTE
                || revisao.getVersaoAtual() != versao.getNumero()) {
            return;
        }

        versao.setZapsignAssinadoEm(LocalDateTime.now());
        concluirResposta(revisao, versao, RevisaoDocumentoStatus.APROVADO, null, null);
        atualizarStatusDominio(revisao, RevisaoDocumentoStatus.APROVADO);
        notificarEquipe(revisao, "Contrato assinado e aprovado pelo cliente",
                "O cliente assinou na ZapSign a versão " + revisao.getVersaoAtual() + " do contrato.");
    }

    private void validarSegredoWebhook(String recebido) {
        String esperado = zapSignProperties.getWebhookSecret();
        if (!StringUtils.hasText(esperado)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Webhook da ZapSign não configurado");
        }
        byte[] esperadoBytes = esperado.getBytes(StandardCharsets.UTF_8);
        byte[] recebidoBytes = recebido == null ? new byte[0] : recebido.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperadoBytes, recebidoBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook da ZapSign não autorizado");
        }
    }

    private void enviarAoCliente(RevisaoDocumento revisao, boolean novaVersao) {
        String tipo = documentoNome(revisao).toLowerCase(Locale.ROOT);
        String link = frontendUrl.replaceAll("/+$", "") + "/revisao/" + revisao.getToken();
        String saudacao = StringUtils.hasText(revisao.getDestinatarioNome())
                ? "Olá, " + revisao.getDestinatarioNome().trim() + "! " : "Olá! ";
        String mensagem = saudacao + "A " + tipo + " da empresa " + revisao.getEmpresaNome()
                + (novaVersao ? " recebeu uma nova versão" : " está disponível para sua análise")
                + ". Você pode marcar trechos, comentar, solicitar revisão, aprovar ou reprovar pelo link seguro abaixo. "
                + "O acesso fica disponível até " + revisao.getTokenExpiraEm().format(DATA_EMAIL) + ".";
        boolean enviado = emailService.enviarEmailComBotao(
                revisao.getDestinatarioEmail(),
                (novaVersao ? "Nova versão para análise: " : "Documento para análise: ") + documentoNome(revisao),
                novaVersao ? "Nova versão disponível" : documentoNome(revisao) + " disponível para análise",
                mensagem,
                "Revisar documento",
                link,
                "Este link é pessoal. Não o encaminhe para terceiros."
        );
        revisao.setEmailStatus(enviado ? "ENVIADO" : "FALHOU");
        revisao.setEmailEnviadoEm(enviado ? LocalDateTime.now() : null);
        revisao.setAtualizadoEm(LocalDateTime.now());
        revisaoRepository.save(revisao);
    }

    private void notificarEquipe(RevisaoDocumento revisao, String titulo, String mensagem) {
        Usuario destinatario;
        if (revisao.getTipo() == RevisaoDocumentoTipo.PROPOSTA) {
            destinatario = propostaRepository.findById(revisao.getReferenciaId()).map(Proposta::getUsuario).orElse(null);
        } else {
            Contrato contrato = contratoRepository.findById(revisao.getReferenciaId()).orElse(null);
            destinatario = contrato == null ? null : (contrato.getResponsavel() != null ? contrato.getResponsavel() : contrato.getUsuario());
        }
        if (destinatario != null && StringUtils.hasText(destinatario.getEmail())) {
            String destino = revisao.getTipo() == RevisaoDocumentoTipo.PROPOSTA ? "/propostas" : "/contratos";
            emailService.enviarEmailComBotao(destinatario.getEmail(), titulo, titulo, mensagem,
                    "Abrir no Climbe", frontendUrl.replaceAll("/+$", "") + destino,
                    "A resposta e todo o histórico ficam registrados no Climbe.");
        }
    }

    private RevisaoDocumentoResponseDTO toResponse(RevisaoDocumento revisao) {
        List<RevisaoDocumentoVersao> versoes = versaoRepository.findByRevisaoIdOrderByNumeroDesc(revisao.getId());
        RevisaoDocumentoVersao atual = versoes.stream()
                .filter(v -> v.getNumero() == revisao.getVersaoAtual())
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versão atual não encontrada"));
        int totalPaginas = totalPaginas(atual);
        Map<Long, List<RevisaoDocumentoAnotacao>> anotacoesPorVersao = anotacaoRepository
                .findByRevisaoIdWithVersao(revisao.getId()).stream()
                .collect(Collectors.groupingBy(
                        anotacao -> anotacao.getVersao().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<RevisaoVersaoResponseDTO> versoesDto = versoes.stream()
                .map(versao -> toVersaoResponse(
                        versao,
                        anotacoesPorVersao.getOrDefault(versao.getId(), List.of())))
                .toList();
        return new RevisaoDocumentoResponseDTO(
                revisao.getId(), revisao.getTipo().name(), revisao.getReferenciaId(), revisao.getEmpresaNome(),
                revisao.getDestinatarioEmail(), revisao.getDestinatarioNome(), revisao.getStatus().name(),
                revisao.getVersaoAtual(), atual.getNomeArquivo(), atual.getContentType(), totalPaginas,
                revisao.getJustificativa(), revisao.getTokenExpiraEm(), revisao.getCriadoEm(),
                revisao.getAtualizadoEm(), revisao.getRespondidoEm(), revisao.getEmailStatus(),
                revisao.getEmailEnviadoEm(), assinaturaUrl(revisao, atual), versoesDto
        );
    }

    private String assinaturaUrl(RevisaoDocumento revisao, RevisaoDocumentoVersao versao) {
        if (revisao.getTipo() != RevisaoDocumentoTipo.CONTRATO
                || revisao.getStatus() != RevisaoDocumentoStatus.AGUARDANDO_CLIENTE
                || !StringUtils.hasText(versao.getZapsignSignatarioToken())) {
            return null;
        }
        return zapSignClient.montarUrlAssinatura(versao.getZapsignSignatarioToken());
    }

    private RevisaoVersaoResponseDTO toVersaoResponse(
            RevisaoDocumentoVersao versao,
            List<RevisaoDocumentoAnotacao> anotacoesPersistidas) {
        List<RevisaoAnotacaoResponseDTO> anotacoes = anotacoesPersistidas.stream()
                .map(a -> new RevisaoAnotacaoResponseDTO(a.getId(), a.getPagina(), a.getPosicaoX(), a.getPosicaoY(),
                        a.getLargura(), a.getAltura(), a.getCor(), a.getComentario()))
                .toList();
        return new RevisaoVersaoResponseDTO(versao.getId(), versao.getNumero(), versao.getNomeArquivo(),
                versao.getContentType(), totalPaginas(versao), versao.getResultado().name(), versao.getComentarioGeral(),
                versao.getJustificativa(), versao.getCriadoEm(), versao.getRespondidoEm(), anotacoes);
    }

    private int totalPaginas(RevisaoDocumentoVersao versao) {
        return versao.getTotalPaginas();
    }

    private RevisaoDocumento buscarTokenValido(String token) {
        RevisaoDocumento revisao = revisaoRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link de revisão inválido"));
        if (revisao.getTokenExpiraEm().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Este link de revisão expirou. Solicite um novo envio à equipe Climbe");
        }
        return revisao;
    }

    private RevisaoDocumento buscarPorId(Long id) {
        return revisaoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revisão não encontrada"));
    }

    private RevisaoDocumentoVersao versaoAtual(RevisaoDocumento revisao) {
        return versaoRepository.findByRevisaoIdAndNumero(revisao.getId(), revisao.getVersaoAtual())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versão atual não encontrada"));
    }

    private void garantirAguardandoCliente(RevisaoDocumento revisao) {
        if (revisao.getStatus() != RevisaoDocumentoStatus.AGUARDANDO_CLIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta versão já recebeu uma resposta");
        }
    }

    private void garantirAssinaturaNaoIniciada(RevisaoDocumento revisao, RevisaoDocumentoVersao versao) {
        if (revisao.getTipo() == RevisaoDocumentoTipo.CONTRATO
                && StringUtils.hasText(versao.getZapsignDocumentoToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A assinatura deste contrato já foi iniciada na ZapSign");
        }
    }

    private void validarAnotacao(RevisaoAnotacaoRequestDTO item, int totalPaginas) {
        if (item.pagina() > totalPaginas) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A marcação referencia uma página inexistente");
        }
        if (item.x().add(item.largura()).compareTo(BigDecimal.ONE) > 0
                || item.y().add(item.altura()).compareTo(BigDecimal.ONE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A marcação ultrapassa os limites da página");
        }
    }

    private void validarPermissao(RevisaoDocumento revisao, Long usuarioId) {
        PermissaoCodigo permissao = revisao.getTipo() == RevisaoDocumentoTipo.PROPOSTA
                ? PermissaoCodigo.PROPOSTA_CRUD : PermissaoCodigo.CONTRATO_CRUD;
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para alterar esta revisão");
        }
    }

    private void atualizarDestinatario(RevisaoDocumento revisao) {
        Empresa empresa = revisao.getEmpresa();
        if (empresa == null || !StringUtils.hasText(empresa.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A empresa não possui e-mail cadastrado");
        }
        revisao.setDestinatarioEmail(empresa.getEmail().trim());
        revisao.setDestinatarioNome(empresa.getRepresentanteNome());
    }

    private void renovarAcesso(RevisaoDocumento revisao) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        revisao.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        revisao.setTokenExpiraEm(LocalDateTime.now().plusDays(Math.max(1, diasExpiracao)));
        revisao.setAtualizadoEm(LocalDateTime.now());
        revisaoRepository.save(revisao);
    }

    private String documentoNome(RevisaoDocumento revisao) {
        return revisao.getTipo() == RevisaoDocumentoTipo.PROPOSTA ? "Proposta" : "Contrato";
    }

    private String limparOpcional(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
