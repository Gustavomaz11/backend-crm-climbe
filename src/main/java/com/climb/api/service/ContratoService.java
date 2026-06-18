package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.Empresa;
import com.climb.api.model.HistoricoAprovacaoContrato;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Proposta;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.HistoricoAprovacaoContratoResponseDTO;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.HistoricoAprovacaoContratoRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.UsuarioRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContratoService {

    public static final String STATUS_PENDENTE = "PENDENTE";
    public static final String STATUS_APROVADO = "APROVADO";
    public static final String STATUS_REJEITADO = "REJEITADO";

    private final ContratoRepository repository;
    private final PropostaRepository propostaRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistoricoAprovacaoContratoRepository historicoRepository;
    private final ContratoNotificacaoService contratoNotificacaoService;
    private final CloudflareR2ArquivoStorageService arquivoStorageService;
    private final RbacService rbacService;
    private final int diasAvisoVencimento;

    public ContratoService(ContratoRepository repository,
                           PropostaRepository propostaRepository,
                           EmpresaRepository empresaRepository,
                           UsuarioRepository usuarioRepository,
                           HistoricoAprovacaoContratoRepository historicoRepository,
                           ContratoNotificacaoService contratoNotificacaoService,
                           CloudflareR2ArquivoStorageService arquivoStorageService,
                           RbacService rbacService,
                           @Value("${app.contract-notifications.expiration-warning-days:30}") int diasAvisoVencimento) {
        this.repository = repository;
        this.propostaRepository = propostaRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoRepository = historicoRepository;
        this.contratoNotificacaoService = contratoNotificacaoService;
        this.arquivoStorageService = arquivoStorageService;
        this.rbacService = rbacService;
        this.diasAvisoVencimento = diasAvisoVencimento;
    }

    public List<Contrato> listar() {
        return repository.findAll();
    }

    public Contrato buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato nao encontrado"));
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

    public Contrato criarComArquivo(Long empresaId, Long propostaId, Long usuarioId, MultipartFile arquivo) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para criar contratos");
        }

        validarEmpresaObrigatoria(empresaId);

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa nao encontrada"));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
        Proposta proposta = buscarPropostaOpcionalAprovada(propostaId, empresaId);

        String prefixo = "contratos/empresa-" + empresa.getIdEmpresa();
        String url = arquivoStorageService.salvar(arquivo, prefixo).url();

        Contrato contrato = new Contrato();
        contrato.setProposta(proposta);
        contrato.setUsuario(usuario);
        contrato.setEmpresa(empresa);
        contrato.setEmpresaNomeFantasia(empresa.getNomeFantasia());
        contrato.setDataInicio(LocalDate.now());
        contrato.setStatus(STATUS_PENDENTE);
        contrato.setUrlPdf(url);

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
        if (atualizado.getProposta() != null) {
            contrato.setProposta(atualizado.getProposta());
            sincronizarCamposDaProposta(contrato);
        }
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
        contrato.setUrlPdf(arquivoStorageService.salvar(arquivo, "contratos/contrato-" + id).url());
        return repository.save(contrato);
    }

    public ResponseEntity<Resource> baixarPdf(Long id) {
        Contrato contrato = buscarPorId(id);
        if (!StringUtils.hasText(contrato.getUrlPdf())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato sem PDF cadastrado");
        }

        try {
            byte[] conteudo = arquivoStorageService.baixar(contrato.getUrlPdf());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"contrato-" + id + ".pdf\"")
                    .body(new ByteArrayResource(conteudo));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao ler PDF do contrato", e);
        }
    }

    public String gerarUrlDownload(Long id) {
        Contrato contrato = buscarPorId(id);
        if (!StringUtils.hasText(contrato.getUrlPdf())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo do contrato nao encontrado");
        }
        return arquivoStorageService.gerarUrlTemporariaDownload(contrato.getUrlPdf());
    }

    @Transactional
    public Contrato aprovar(Long id, Long usuarioId, String statusNovo) {
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
        if (!rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para aprovar/rejeitar contratos");
        }

        String statusNormalizado = normalizarStatusAprovacao(statusNovo);
        Contrato contrato = buscarPorId(id);
        String statusAnterior = contrato.getStatus();

        if (statusNormalizado.equals(statusAnterior)) {
            return contrato;
        }

        if (STATUS_APROVADO.equals(statusAnterior) || STATUS_REJEITADO.equals(statusAnterior)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é permitido alterar o status de um contrato já aprovado ou rejeitado");
        }

        contrato.setStatus(statusNormalizado);
        Contrato salvo = repository.save(contrato);

        HistoricoAprovacaoContrato historico = new HistoricoAprovacaoContrato();
        historico.setContratoId(salvo.getIdContrato());
        historico.setUsuarioId(usuarioId);
        historico.setStatusAnterior(statusAnterior);
        historico.setStatusNovo(statusNormalizado);
        historico.setDataAlteracao(LocalDateTime.now());
        historicoRepository.save(historico);

        return salvo;
    }

    public List<HistoricoAprovacaoContratoResponseDTO> listarHistorico(Long contratoId) {
        if (contratoId == null || !repository.existsById(contratoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato nao encontrado");
        }

        List<HistoricoAprovacaoContrato> historico = historicoRepository.findByContratoIdOrderByDataAlteracaoDesc(contratoId);
        Map<Long, Usuario> usuariosPorId = buscarUsuariosDoHistorico(historico);

        return historico.stream()
                .map(h -> new HistoricoAprovacaoContratoResponseDTO(
                        h.getIdHistorico(),
                        h.getContratoId(),
                        h.getUsuarioId(),
                        usuariosPorId.get(h.getUsuarioId()) != null ? usuariosPorId.get(h.getUsuarioId()).getNomeCompleto() : null,
                        h.getStatusAnterior(),
                        h.getStatusNovo(),
                        h.getDataAlteracao()
                ))
                .toList();
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

    private Proposta buscarPropostaOpcionalAprovada(Long propostaId, Long empresaId) {
        if (propostaId == null) {
            return null;
        }

        Proposta proposta = propostaRepository.findById(propostaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta nao encontrada"));

        if (proposta.getStatus() != PropostaStatus.APROVADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A proposta vinculada precisa estar aprovada");
        }

        if (proposta.getEmpresa() == null || !Objects.equals(proposta.getEmpresa().getIdEmpresa(), empresaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A proposta vinculada deve pertencer à empresa selecionada");
        }

        return proposta;
    }

    private Map<Long, Usuario> buscarUsuariosDoHistorico(List<HistoricoAprovacaoContrato> historico) {
        List<Long> usuarioIds = historico.stream()
                .map(HistoricoAprovacaoContrato::getUsuarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (usuarioIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return usuarioRepository.findAllById(usuarioIds)
                .stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    private boolean statusIgnoradoParaVencimento(String status) {
        if (status == null) {
            return false;
        }

        return Set.of("ENCERRADO", "CANCELADO", "INATIVO", STATUS_REJEITADO).contains(status.toUpperCase());
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
            return;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposta e obrigatoria neste fluxo");
        }

        return contrato.getProposta().getIdProposta();
    }

    private String normalizarStatusAprovacao(String status) {
        if (!StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status é obrigatório");
        }

        String normalizado = status.trim().toUpperCase();
        if (!STATUS_APROVADO.equals(normalizado) && !STATUS_REJEITADO.equals(normalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido. Use APROVADO ou REJEITADO");
        }
        return normalizado;
    }

    private void validarEmpresaObrigatoria(Long empresaId) {
        if (empresaId == null || empresaId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma empresa para o contrato");
        }
    }
}
