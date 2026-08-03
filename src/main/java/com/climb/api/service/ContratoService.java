package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoParcela;
import com.climb.api.model.Empresa;
import com.climb.api.model.HistoricoAprovacaoContrato;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Proposta;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.HistoricoAprovacaoContratoResponseDTO;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.ContratoKanbanTaskRepository;
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
import java.util.HashSet;
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
    private final ContratoKanbanTaskRepository taskRepository;
    private final PropostaRepository propostaRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistoricoAprovacaoContratoRepository historicoRepository;
    private final ContratoNotificacaoService contratoNotificacaoService;
    private final CloudflareR2ArquivoStorageService arquivoStorageService;
    private final RbacService rbacService;
    private final RevisaoDocumentoService revisaoDocumentoService;
    private final ContratoParcelaCalculator parcelaCalculator;
    private final int diasAvisoVencimento;

    public ContratoService(ContratoRepository repository,
                           ContratoKanbanTaskRepository taskRepository,
                           PropostaRepository propostaRepository,
                           EmpresaRepository empresaRepository,
                           UsuarioRepository usuarioRepository,
                           HistoricoAprovacaoContratoRepository historicoRepository,
                           ContratoNotificacaoService contratoNotificacaoService,
                           CloudflareR2ArquivoStorageService arquivoStorageService,
                           RbacService rbacService,
                           RevisaoDocumentoService revisaoDocumentoService,
                           ContratoParcelaCalculator parcelaCalculator,
                           @Value("${app.contract-notifications.expiration-warning-days:30}") int diasAvisoVencimento) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.propostaRepository = propostaRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoRepository = historicoRepository;
        this.contratoNotificacaoService = contratoNotificacaoService;
        this.arquivoStorageService = arquivoStorageService;
        this.rbacService = rbacService;
        this.revisaoDocumentoService = revisaoDocumentoService;
        this.parcelaCalculator = parcelaCalculator;
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
        Long propostaId = obterPropostaId(contrato);
        validarPropostaDisponivelParaNovoContrato(propostaId);
        contrato.setProposta(propostaRepository.findById(propostaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposta nao encontrada")));
        sincronizarCamposDaProposta(contrato);
        normalizarResponsavelEParticipantes(contrato);
        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoCriado(salvo);
        return salvo;
    }

    @Transactional
    public Contrato criarAPartirDoPipeline(Empresa empresa, Usuario usuario, Usuario responsavel) {
        if (empresa == null || usuario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa e usuário são obrigatórios para converter o negócio");
        }

        Contrato contrato = new Contrato();
        contrato.setEmpresa(empresa);
        contrato.setEmpresaNomeFantasia(empresa.getNomeFantasia());
        contrato.setUsuario(usuario);
        contrato.setResponsavel(responsavel != null ? responsavel : usuario);
        contrato.setDataInicio(LocalDate.now());
        contrato.setStatus(STATUS_PENDENTE);
        contrato.getParticipantes().add(contrato.getResponsavel());

        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoCriado(salvo);
        return salvo;
    }

    @Transactional
    public Contrato criarComArquivo(Long empresaId,
                                    Long propostaId,
                                    Long usuarioId,
                                    Long responsavelId,
                                    List<Long> participanteIds,
                                    MultipartFile arquivo) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para criar contratos");
        }

        validarEmpresaObrigatoria(empresaId);
        validarResponsavelObrigatorio(responsavelId);
        validarParticipantesObrigatorios(participanteIds);

        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa nao encontrada"));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
        Proposta proposta = buscarPropostaOpcionalAprovada(propostaId, empresaId);
        if (proposta != null) {
            validarPropostaDisponivelParaNovoContrato(proposta.getIdProposta());
        }
        Usuario responsavel = buscarUsuarioOuFalhar(responsavelId, "Responsável do contrato não encontrado");
        Set<Usuario> participantes = buscarParticipantes(participanteIds);
        revisaoDocumentoService.validarEnvio(empresa, arquivo);

        String prefixo = "contratos/empresa-" + empresa.getIdEmpresa();
        ArquivoUploadResponseDTO upload = arquivoStorageService.salvar(arquivo, prefixo);

        Contrato contrato = new Contrato();
        contrato.setProposta(proposta);
        contrato.setUsuario(usuario);
        contrato.setEmpresa(empresa);
        contrato.setEmpresaNomeFantasia(empresa.getNomeFantasia());
        contrato.setDataInicio(LocalDate.now());
        contrato.setStatus(STATUS_PENDENTE);
        contrato.setUrlPdf(upload.url());
        contrato.setResponsavel(responsavel);
        contrato.setParticipantes(participantes);
        contrato.setServico(proposta != null ? proposta.getServico() : null);
        normalizarParticipantes(contrato);

        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoCriado(salvo);
        revisaoDocumentoService.iniciarContrato(salvo, upload, usuario);
        return salvo;
    }

    public Contrato atualizar(Long id, Contrato atualizado) {
        Contrato contrato = buscarPorId(id);
        Contrato anterior = snapshot(contrato);
        contrato.setDataInicio(atualizado.getDataInicio());
        contrato.setDataFim(atualizado.getDataFim());
        contrato.setStatus(atualizado.getStatus());
        atualizarResponsavelEParticipantes(contrato, atualizado);
        if (atualizado.getProposta() != null) {
            Long propostaId = obterPropostaId(atualizado);
            validarPropostaDisponivelParaContratoExistente(propostaId, id);
            contrato.setProposta(atualizado.getProposta());
            sincronizarCamposDaProposta(contrato);
        }
        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoAtualizado(anterior, salvo);
        return salvo;
    }

    @Transactional
    public Contrato atualizarResponsaveis(Long id, Long usuarioId, Long responsavelId, List<Long> participanteIds) {
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
        if (!rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para editar contratos");
        }

        Contrato contrato = buscarPorId(id);
        Contrato anterior = snapshot(contrato);
        aplicarResponsavelEParticipantes(contrato, responsavelId, participanteIds);
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
        if (STATUS_APROVADO.equals(statusNormalizado)) {
            contrato.setDataAprovacao(LocalDate.now());
            gerarParcelas(contrato);
        }
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

    @Transactional
    public Contrato desvincularProposta(Long id, Long usuarioId) {
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
        if (!rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para editar contratos");
        }

        Contrato contrato = buscarPorId(id);
        if (contrato.getProposta() == null) {
            return contrato;
        }

        Contrato anterior = snapshot(contrato);
        contrato.setProposta(null);
        Contrato salvo = repository.save(contrato);
        contratoNotificacaoService.notificarContratoAtualizado(anterior, salvo);
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

    private void validarPropostaDisponivelParaNovoContrato(Long propostaId) {
        if (propostaId != null && repository.existsByProposta_IdProposta(propostaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta proposta já está vinculada a outro contrato");
        }
    }

    private void validarPropostaDisponivelParaContratoExistente(Long propostaId, Long contratoId) {
        if (propostaId != null && repository.existsByProposta_IdPropostaAndIdContratoNot(propostaId, contratoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta proposta já está vinculada a outro contrato");
        }
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
        snapshot.setResponsavel(contrato.getResponsavel());
        snapshot.setParticipantes(new HashSet<>(contrato.getParticipantes()));
        snapshot.setEmpresa(contrato.getEmpresa());
        snapshot.setEmpresaNomeFantasia(contrato.getEmpresaNomeFantasia());
        snapshot.setDataInicio(contrato.getDataInicio());
        snapshot.setDataFim(contrato.getDataFim());
        snapshot.setUrlPdf(contrato.getUrlPdf());
        snapshot.setStatus(contrato.getStatus());
        snapshot.setServico(contrato.getServico());
        snapshot.setDataAprovacao(contrato.getDataAprovacao());
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
        contrato.setServico(proposta.getServico());
    }

    @Transactional
    public Contrato alterarVencimentoParcela(Long id, Long parcelaId, Long usuarioId, LocalDate vencimento) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para editar contratos");
        }
        if (vencimento == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vencimento é obrigatório");
        }
        Contrato contrato = buscarPorId(id);
        ContratoParcela parcela = contrato.getParcelas().stream()
                .filter(item -> Objects.equals(item.getId(), parcelaId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcela não encontrada neste contrato"));
        parcela.setVencimento(vencimento);
        return repository.save(contrato);
    }

    private void gerarParcelas(Contrato contrato) {
        if (contrato.getProposta() == null || !contrato.getParcelas().isEmpty()) {
            return;
        }
        List<ContratoParcela> parcelas = parcelaCalculator.calcular(contrato.getProposta(), contrato.getDataAprovacao())
                .stream()
                .map(planejada -> {
                    ContratoParcela parcela = new ContratoParcela();
                    parcela.setNumero(planejada.numero());
                    parcela.setCompetencia(planejada.competencia());
                    parcela.setVencimento(planejada.vencimento());
                    parcela.setValor(planejada.valor());
                    parcela.setStatus("PENDENTE");
                    return parcela;
                })
                .toList();
        contrato.setParcelas(parcelas);
        if (!parcelas.isEmpty()) {
            contrato.setDataInicio(parcelas.getFirst().getCompetencia());
            contrato.setDataFim(parcelas.getLast().getCompetencia().withDayOfMonth(
                    parcelas.getLast().getCompetencia().lengthOfMonth()));
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

    private void normalizarResponsavelEParticipantes(Contrato contrato) {
        if (contrato.getResponsavel() != null && contrato.getResponsavel().getId() != null) {
            contrato.setResponsavel(usuarioRepository.findById(contrato.getResponsavel().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável do contrato não encontrado")));
        }
        if (contrato.getResponsavel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável do contrato é obrigatório");
        }
        List<Long> participanteIds = contrato.getParticipantes() == null
                ? null
                : contrato.getParticipantes().stream().map(Usuario::getId).toList();
        validarParticipantesObrigatorios(participanteIds);
        contrato.setParticipantes(buscarParticipantes(participanteIds));
        normalizarParticipantes(contrato);
    }

    private void atualizarResponsavelEParticipantes(Contrato contrato, Contrato atualizado) {
        if (atualizado.getResponsavel() != null && atualizado.getResponsavel().getId() != null) {
            List<Long> participanteIds = atualizado.getParticipantes() == null
                    ? contrato.getParticipantes().stream().map(Usuario::getId).toList()
                    : atualizado.getParticipantes().stream()
                    .map(Usuario::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            aplicarResponsavelEParticipantes(contrato, atualizado.getResponsavel().getId(), participanteIds);
            return;
        }
        if (atualizado.getParticipantes() != null && !atualizado.getParticipantes().isEmpty()) {
            aplicarResponsavelEParticipantes(
                    contrato,
                    contrato.getResponsavel() != null ? contrato.getResponsavel().getId() : null,
                    atualizado.getParticipantes().stream()
                            .map(Usuario::getId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList()
            );
            return;
        }
        normalizarParticipantes(contrato);
    }

    private void normalizarParticipantes(Contrato contrato) {
        if (contrato.getParticipantes() == null) {
            contrato.setParticipantes(new HashSet<>());
        }
        if (contrato.getUsuario() != null) {
            contrato.getParticipantes().add(contrato.getUsuario());
        }
        if (contrato.getResponsavel() != null) {
            contrato.getParticipantes().add(contrato.getResponsavel());
        }
    }

    private void validarResponsavelObrigatorio(Long responsavelId) {
        if (responsavelId == null || responsavelId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um responsável para o contrato");
        }
    }

    private void validarParticipantesObrigatorios(List<Long> participanteIds) {
        if (participanteIds == null || participanteIds.stream().filter(Objects::nonNull).distinct().findAny().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos um ator para o contrato");
        }
    }

    private Set<Usuario> buscarParticipantes(List<Long> participanteIds) {
        return participanteIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(usuarioId -> buscarUsuarioOuFalhar(usuarioId, "Ator do contrato não encontrado"))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Usuario buscarUsuarioOuFalhar(Long usuarioId, String mensagem) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, mensagem));
    }

    private void aplicarResponsavelEParticipantes(Contrato contrato, Long responsavelId, List<Long> participanteIds) {
        validarResponsavelObrigatorio(responsavelId);
        validarParticipantesObrigatorios(participanteIds);

        Usuario responsavel = buscarUsuarioOuFalhar(responsavelId, "Responsável do contrato não encontrado");
        Set<Usuario> participantes = buscarParticipantes(participanteIds);
        if (contrato.getUsuario() != null) {
            participantes.add(contrato.getUsuario());
        }
        participantes.add(responsavel);

        validarParticipantesRemovidosSemTasks(contrato, participantes);

        contrato.setResponsavel(responsavel);
        contrato.setParticipantes(participantes);
    }

    private void validarParticipantesRemovidosSemTasks(Contrato contrato, Set<Usuario> novosParticipantes) {
        if (contrato.getIdContrato() == null || contrato.getParticipantes() == null) {
            return;
        }

        Set<Long> novosIds = novosParticipantes.stream()
                .map(Usuario::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Usuario> removidos = contrato.getParticipantes().stream()
                .filter(participante -> participante.getId() != null && !novosIds.contains(participante.getId()))
                .toList();
        if (removidos.isEmpty()) return;

        Set<Long> removidosComTasks = taskRepository.findResponsavelIdsComTasks(
                contrato.getIdContrato(),
                removidos.stream().map(Usuario::getId).collect(Collectors.toSet()));
        removidos.stream()
                .filter(removido -> removidosComTasks.contains(removido.getId()))
                .findFirst()
                .ifPresent(removido -> {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Não é possível remover " + removido.getNomeCompleto()
                                    + " porque há tarefas vinculadas a este participante");
                });
    }
}
