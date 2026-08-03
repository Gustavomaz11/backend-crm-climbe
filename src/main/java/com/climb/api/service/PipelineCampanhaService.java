package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class PipelineCampanhaService {
    private final PipelineCampanhaRepository repository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PipelineScriptRepository scriptRepository;
    private final PipelineCampanhaValidator validator;
    private final PipelineCampanhaMapper mapper;
    private final PipelineCampanhaExecucaoManager execucaoManager;
    private final PipelineCadenciaEngine cadenciaEngine;
    private final RbacService rbacService;

    public PipelineCampanhaService(PipelineCampanhaRepository repository,
                                   PipelineVendasNegocioRepository negocioRepository,
                                   UsuarioRepository usuarioRepository,
                                   PipelineScriptRepository scriptRepository,
                                   PipelineCampanhaValidator validator,
                                   PipelineCampanhaMapper mapper,
                                   PipelineCampanhaExecucaoManager execucaoManager,
                                   PipelineCadenciaEngine cadenciaEngine,
                                   RbacService rbacService) {
        this.repository = repository;
        this.negocioRepository = negocioRepository;
        this.usuarioRepository = usuarioRepository;
        this.scriptRepository = scriptRepository;
        this.validator = validator;
        this.mapper = mapper;
        this.execucaoManager = execucaoManager;
        this.cadenciaEngine = cadenciaEngine;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public List<PipelineCampanhaResponseDTO> listar(Long usuarioId) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_VISUALIZAR);
        return repository.findAllByOrderByCriadoEmDesc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineCampanhaLeadDTO> listarLeads(Long usuarioId) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_VISUALIZAR);
        return negocioRepository.findByResultadoOrderByCriadoEmDesc(PipelineVendasResultado.ABERTO).stream()
                .map(negocio -> new PipelineCampanhaLeadDTO(
                        negocio.getIdNegocio(), negocio.getNomeEmpresa(), negocio.getNomeContato(),
                        negocio.getResponsavel().getNomeCompleto(), negocio.getServicoInteresse(),
                        negocio.getFunil().getNome(), negocio.getEtapa().getNome()))
                .toList();
    }

    @Transactional
    public PipelineCampanhaResponseDTO criar(Long usuarioId, PipelineCampanhaRequestDTO dto) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_CRIAR);
        validator.validar(dto);
        PipelineCampanha campanha = new PipelineCampanha();
        campanha.setCriadoPor(usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado")));
        aplicar(campanha, dto);
        PipelineCampanha salva = repository.save(campanha);
        if (Boolean.TRUE.equals(salva.getAtivo())) iniciar(salva, usuarioId);
        return mapper.toResponse(salva);
    }

    @Transactional
    public PipelineCampanhaResponseDTO atualizar(Long id, Long usuarioId, PipelineCampanhaRequestDTO dto) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_EDITAR);
        validator.validar(dto);
        PipelineCampanha campanha = buscar(id);
        if (Boolean.TRUE.equals(campanha.getAtivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inative a campanha antes de alterar sua configuração");
        }
        aplicar(campanha, dto);
        PipelineCampanha salva = repository.save(campanha);
        if (Boolean.TRUE.equals(salva.getAtivo())) iniciar(salva, usuarioId);
        return mapper.toResponse(salva);
    }

    @Transactional
    public PipelineCampanhaResponseDTO alterarAtivacao(Long id, Long usuarioId, boolean ativo) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_EXECUTAR);
        PipelineCampanha campanha = buscar(id);
        campanha.setAtivo(ativo);
        PipelineCampanha salva = repository.save(campanha);
        execucaoManager.sincronizar(salva);
        if (ativo) cadenciaEngine.processarPendentes();
        return mapper.toResponse(salva);
    }

    private void iniciar(PipelineCampanha campanha, Long usuarioId) {
        exigir(usuarioId, PermissaoCodigo.COMERCIAL_CAMPANHA_EXECUTAR);
        execucaoManager.sincronizar(campanha);
        cadenciaEngine.processarPendentes();
    }

    private void aplicar(PipelineCampanha campanha, PipelineCampanhaRequestDTO dto) {
        campanha.setNome(dto.nome().trim());
        campanha.setEstrategia(dto.estrategia().trim());
        campanha.setDescricao(normalizar(dto.descricao()));
        campanha.setLeads(resolverLeads(dto.leadIds()));
        campanha.setParticipantes(resolverParticipantes(dto.participanteIds()));
        campanha.setScripts(resolverScripts(idsScripts(dto)));
        campanha.setDiasExecucao(new LinkedHashSet<>(dto.diasExecucao()));
        campanha.substituirEtapas(criarEtapas(dto));
        campanha.setAtivo(dto.ativo());
    }

    private List<PipelineCadenciaEtapa> criarEtapas(PipelineCampanhaRequestDTO dto) {
        return IntStream.range(0, dto.etapas().size()).mapToObj(indice -> {
            PipelineCadenciaEtapaRequestDTO etapaDto = dto.etapas().get(indice);
            PipelineCadenciaEtapa etapa = new PipelineCadenciaEtapa();
            etapa.setOrdem(indice);
            etapa.setTipo(etapaDto.tipo());
            etapa.setTitulo(normalizar(etapaDto.titulo()));
            etapa.setDescricao(normalizar(etapaDto.descricao()));
            etapa.setTipoTarefa(normalizar(etapaDto.tipoTarefa()));
            etapa.setPrioridade(etapaDto.prioridade());
            etapa.setDiasUteisEspera(etapaDto.diasUteisEspera() == null ? 0 : etapaDto.diasUteisEspera());
            etapa.setPrazoDiasUteis(etapaDto.prazoDiasUteis());
            etapa.setScript(etapaDto.scriptId() == null ? null : buscarScriptAtivo(etapaDto.scriptId()));
            return etapa;
        }).toList();
    }

    private Set<Long> idsScripts(PipelineCampanhaRequestDTO dto) {
        Set<Long> ids = new LinkedHashSet<>(dto.scriptIds() == null ? Set.of() : dto.scriptIds());
        dto.etapas().stream().map(PipelineCadenciaEtapaRequestDTO::scriptId).filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private Set<PipelineVendasNegocio> resolverLeads(Set<Long> ids) {
        Set<PipelineVendasNegocio> leads = new LinkedHashSet<>(negocioRepository.findAllById(ids));
        if (leads.size() != ids.size() || leads.stream().anyMatch(item -> item.getResultado() != PipelineVendasResultado.ABERTO)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione apenas leads abertos e existentes");
        }
        return leads;
    }

    private Set<Usuario> resolverParticipantes(Set<Long> ids) {
        Set<Usuario> usuarios = new LinkedHashSet<>(usuarioRepository.findAllById(ids));
        if (usuarios.size() != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participante não encontrado");
        return usuarios;
    }

    private Set<PipelineScript> resolverScripts(Set<Long> ids) {
        Set<PipelineScript> scripts = ids.stream().map(this::buscarScriptAtivo).collect(Collectors.toCollection(LinkedHashSet::new));
        if (scripts.size() != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script não encontrado");
        return scripts;
    }

    private PipelineScript buscarScriptAtivo(Long id) {
        return scriptRepository.findById(id).filter(script -> Boolean.TRUE.equals(script.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script ativo não encontrado"));
    }

    private PipelineCampanha buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campanha não encontrada"));
    }

    private void exigir(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para campanhas comerciais");
        }
    }

    private String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
}
