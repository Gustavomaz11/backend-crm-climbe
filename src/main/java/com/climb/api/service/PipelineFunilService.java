package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.PipelineVendasFunilRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PipelineFunilService {
    private final PipelineVendasFunilRepository repository;
    private final RbacService rbacService;
    private final PipelineFunilValidator validator;
    private final PipelineFunilMapper mapper;

    public PipelineFunilService(PipelineVendasFunilRepository repository,
                                RbacService rbacService,
                                PipelineFunilValidator validator,
                                PipelineFunilMapper mapper) {
        this.repository = repository;
        this.rbacService = rbacService;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PipelineFunilResumoResponseDTO> listarAtivos(Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL);
        return repository.findByAtivoTrueOrderByPosicaoAsc().stream().map(mapper::toResumo).toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineFunilResponseDTO> listarTodos(Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_FUNIL_VISUALIZAR);
        return repository.findAllByOrderByPosicaoAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public PipelineFunilResponseDTO criar(Long usuarioId, PipelineFunilRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_FUNIL_CRIAR);
        validator.validar(dto);
        PipelineVendasFunil funil = new PipelineVendasFunil();
        funil.setCodigo(gerarCodigo(dto.nome()));
        funil.setPosicao(proximaPosicao());
        aplicarDados(funil, dto);
        sincronizarEtapas(funil, dto.etapas());
        return mapper.toResponse(repository.save(funil));
    }

    @Transactional
    public PipelineFunilResponseDTO atualizar(Long funilId, Long usuarioId, PipelineFunilRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_FUNIL_EDITAR);
        validator.validar(dto);
        PipelineVendasFunil funil = buscar(funilId);
        aplicarDados(funil, dto);
        sincronizarEtapas(funil, dto.etapas());
        return mapper.toResponse(repository.save(funil));
    }

    @Transactional
    public PipelineFunilResponseDTO duplicar(Long funilId, Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_FUNIL_DUPLICAR);
        PipelineVendasFunil original = buscar(funilId);
        PipelineVendasFunil copia = new PipelineVendasFunil();
        copia.setCodigo(gerarCodigo(original.getNome()));
        copia.setNome("Cópia de " + original.getNome());
        copia.setDescricao(original.getDescricao());
        copia.setEstrategia(original.getEstrategia());
        copia.setAtivo(false);
        copia.setPosicao(proximaPosicao());
        original.getEtapas().forEach(etapa -> copia.getEtapas().add(copiarEtapa(copia, etapa)));
        return mapper.toResponse(repository.save(copia));
    }

    @Transactional
    public List<PipelineFunilResponseDTO> reordenar(Long usuarioId, List<Long> ids) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_FUNIL_ORDENAR);
        List<PipelineVendasFunil> funis = repository.findAllByOrderByPosicaoAsc();
        Set<Long> existentes = funis.stream().map(PipelineVendasFunil::getIdFunil).collect(Collectors.toSet());
        if (ids.size() != existentes.size() || !existentes.equals(new HashSet<>(ids))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe todos os funis uma única vez para reordenar");
        }
        Map<Long, PipelineVendasFunil> porId = funis.stream()
                .collect(Collectors.toMap(PipelineVendasFunil::getIdFunil, Function.identity()));
        for (int indice = 0; indice < ids.size(); indice++) porId.get(ids.get(indice)).setPosicao(indice + 1);
        repository.saveAll(funis);
        return ids.stream().map(porId::get).map(mapper::toResponse).toList();
    }

    private void aplicarDados(PipelineVendasFunil funil, PipelineFunilRequestDTO dto) {
        funil.setNome(dto.nome().trim());
        funil.setDescricao(normalizar(dto.descricao()));
        funil.setEstrategia(dto.estrategia().trim());
        funil.setAtivo(dto.ativo());
    }

    private void sincronizarEtapas(PipelineVendasFunil funil, List<PipelineEtapaConfiguracaoRequestDTO> requests) {
        Map<Long, PipelineVendasEtapa> existentes = funil.getEtapas().stream()
                .filter(etapa -> etapa.getIdEtapa() != null)
                .collect(Collectors.toMap(PipelineVendasEtapa::getIdEtapa, Function.identity()));
        Set<Long> recebidas = new HashSet<>();

        for (int indice = 0; indice < requests.size(); indice++) {
            PipelineEtapaConfiguracaoRequestDTO request = requests.get(indice);
            PipelineVendasEtapa etapa = resolverEtapa(funil, request, existentes);
            if (etapa.getIdEtapa() != null) recebidas.add(etapa.getIdEtapa());
            aplicarEtapa(etapa, request, indice + 1);
        }

        existentes.values().stream()
                .filter(etapa -> !recebidas.contains(etapa.getIdEtapa()))
                .forEach(etapa -> etapa.setAtivo(false));
    }

    private PipelineVendasEtapa resolverEtapa(PipelineVendasFunil funil,
                                               PipelineEtapaConfiguracaoRequestDTO request,
                                               Map<Long, PipelineVendasEtapa> existentes) {
        if (request.id() != null) {
            PipelineVendasEtapa etapa = existentes.get(request.id());
            if (etapa == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etapa não pertence ao funil informado");
            return etapa;
        }
        PipelineVendasEtapa etapa = new PipelineVendasEtapa();
        etapa.setFunil(funil);
        etapa.setCodigo(gerarCodigo(request.nome()));
        funil.getEtapas().add(etapa);
        return etapa;
    }

    private void aplicarEtapa(PipelineVendasEtapa etapa,
                              PipelineEtapaConfiguracaoRequestDTO dto,
                              int posicao) {
        etapa.setNome(dto.nome().trim());
        etapa.setObjetivo(normalizar(dto.objetivo()));
        etapa.setCriteriosConclusao(normalizar(dto.criteriosConclusao()));
        etapa.setTempoMaximoPermanenciaDias(dto.tempoMaximoPermanenciaDias());
        etapa.setCamposObrigatorios(dto.camposObrigatorios() == null ? new ArrayList<>() : new ArrayList<>(dto.camposObrigatorios()));
        etapa.setResultado(dto.sucesso() ? PipelineVendasResultado.GANHO : dto.perda() ? PipelineVendasResultado.PERDIDO : PipelineVendasResultado.ABERTO);
        etapa.setPosicao(posicao);
        etapa.setAtivo(dto.ativo());
    }

    private PipelineVendasEtapa copiarEtapa(PipelineVendasFunil funil, PipelineVendasEtapa original) {
        PipelineVendasEtapa copia = new PipelineVendasEtapa();
        copia.setFunil(funil);
        copia.setCodigo(gerarCodigo(original.getNome()));
        copia.setNome(original.getNome());
        copia.setObjetivo(original.getObjetivo());
        copia.setCriteriosConclusao(original.getCriteriosConclusao());
        copia.setTempoMaximoPermanenciaDias(original.getTempoMaximoPermanenciaDias());
        copia.setCamposObrigatorios(new ArrayList<>(camposObrigatorios(original)));
        copia.setResultado(original.getResultado());
        copia.setPosicao(original.getPosicao());
        copia.setAtivo(original.getAtivo());
        return copia;
    }

    private PipelineVendasFunil buscar(Long funilId) {
        return repository.findById(funilId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funil comercial não encontrado"));
    }

    private int proximaPosicao() {
        return repository.findTopByOrderByPosicaoDesc().map(funil -> funil.getPosicao() + 1).orElse(1);
    }

    private String gerarCodigo(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (base.isBlank()) base = "FUNIL";
        if (base.length() > 50) base = base.substring(0, 50);
        return base + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirPermissao(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para configurar funis comerciais");
        }
    }

    private List<String> camposObrigatorios(PipelineVendasEtapa etapa) {
        return etapa.getCamposObrigatorios() == null ? List.of() : etapa.getCamposObrigatorios();
    }
}
