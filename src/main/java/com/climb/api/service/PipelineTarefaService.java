package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PipelineTarefaService {
    private final PipelineVendasTarefaRepository repository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PipelineHistoricoService historicoService;
    private final PipelineCadenciaEngine cadenciaEngine;
    private final RbacService rbacService;

    public PipelineTarefaService(PipelineVendasTarefaRepository repository,
                                 PipelineVendasNegocioRepository negocioRepository,
                                 UsuarioRepository usuarioRepository,
                                 PipelineHistoricoService historicoService,
                                 PipelineCadenciaEngine cadenciaEngine,
                                 RbacService rbacService) {
        this.repository = repository;
        this.negocioRepository = negocioRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoService = historicoService;
        this.cadenciaEngine = cadenciaEngine;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public List<PipelineTarefaResponseDTO> listar(Long usuarioId,
                                                  PipelineTarefaVisao visao,
                                                  Long responsavelId,
                                                  Long negocioId,
                                                  Long funilId,
                                                  String tipo) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR);
        PipelineTarefaVisao visaoEfetiva = visao == null ? PipelineTarefaVisao.TODAS : visao;
        LocalDate hoje = LocalDate.now();
        return repository.findAllByOrderByPrazoAscCriadoEmDesc().stream()
                .distinct()
                .filter(tarefa -> negocioId == null || negocioId.equals(tarefa.getNegocio().getIdNegocio()))
                .filter(tarefa -> funilId == null || funilId.equals(tarefa.getNegocio().getFunil().getIdFunil()))
                .filter(tarefa -> responsavelId == null || responsavelId.equals(tarefa.getResponsavel().getId()))
                .filter(tarefa -> tipo == null || tipo.isBlank() || tarefa.getTipo().equalsIgnoreCase(tipo.trim()))
                .filter(tarefa -> correspondeVisao(tarefa, visaoEfetiva, hoje))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineTarefaResponseDTO> listarDoNegocio(Long negocioId, Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR);
        exigirNegocioExistente(negocioId);
        return repository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(negocioId).stream()
                .distinct()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PipelineTarefaResponseDTO criar(Long negocioId, Long usuarioId, PipelineTarefaRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_CRIAR);
        if (dto.status() == PipelineTarefaStatus.CONCLUIDA) {
            exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR);
        }
        validarDatas(dto.dataInicio(), dto.prazo());
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        PipelineVendasTarefa tarefa = new PipelineVendasTarefa();
        tarefa.setNegocio(negocio);
        tarefa.setCriadoPor(buscarUsuario(usuarioId));
        aplicarDados(tarefa, dto);
        PipelineVendasTarefa salva = repository.save(tarefa);
        historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.CRIACAO_TAREFA,
                "Tarefa criada: " + salva.getTitulo());
        if (salva.getStatus() == PipelineTarefaStatus.CONCLUIDA) {
            historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.CONCLUSAO_TAREFA,
                    "Tarefa concluída: " + salva.getTitulo());
        }
        return toResponse(salva);
    }

    @Transactional
    public PipelineTarefaResponseDTO atualizar(Long tarefaId, Long usuarioId, PipelineTarefaRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_EDITAR);
        validarDatas(dto.dataInicio(), dto.prazo());
        PipelineVendasTarefa tarefa = buscarTarefa(tarefaId);
        PipelineTarefaStatus statusAnterior = tarefa.getStatus();
        exigirPermissaoDeConclusaoSeNecessario(usuarioId, statusAnterior, dto.status());
        aplicarDados(tarefa, dto);
        PipelineVendasTarefa salva = repository.save(tarefa);
        historicoService.registrar(tarefa.getNegocio(), usuarioId, PipelineHistoricoTipo.ALTERACAO_TAREFA,
                "Tarefa atualizada: " + tarefa.getTitulo());
        registrarConclusaoSeNecessario(tarefa, usuarioId, statusAnterior);
        return toResponse(salva);
    }

    @Transactional
    public PipelineTarefaResponseDTO alterarStatus(Long tarefaId,
                                                   Long usuarioId,
                                                   PipelineTarefaStatus status) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR);
        PipelineVendasTarefa tarefa = buscarTarefa(tarefaId);
        PipelineTarefaStatus statusAnterior = tarefa.getStatus();
        tarefa.setStatus(status);
        atualizarDataConclusao(tarefa);
        PipelineVendasTarefa salva = repository.save(tarefa);
        registrarConclusaoSeNecessario(tarefa, usuarioId, statusAnterior);
        if (status != PipelineTarefaStatus.CONCLUIDA) {
            historicoService.registrar(tarefa.getNegocio(), usuarioId, PipelineHistoricoTipo.ALTERACAO_TAREFA,
                    "Status da tarefa \"" + tarefa.getTitulo() + "\" alterado para " + status.name());
        }
        return toResponse(salva);
    }

    private void aplicarDados(PipelineVendasTarefa tarefa, PipelineTarefaRequestDTO dto) {
        tarefa.setTitulo(dto.titulo().trim());
        tarefa.setDescricao(normalizar(dto.descricao()));
        tarefa.setResponsavel(buscarUsuario(dto.responsavelId()));
        tarefa.setDataInicio(dto.dataInicio());
        tarefa.setPrazo(dto.prazo());
        tarefa.setPrioridade(dto.prioridade());
        tarefa.setStatus(dto.status());
        tarefa.setTipo(dto.tipo().trim());
        tarefa.setObservacoes(normalizar(dto.observacoes()));
        atualizarDataConclusao(tarefa);
        if (dto.subtarefas() != null) {
            tarefa.substituirSubtarefas(criarSubtarefas(dto.subtarefas()));
        }
    }

    private List<PipelineVendasSubtarefa> criarSubtarefas(List<PipelineSubtarefaRequestDTO> subtarefas) {
        return java.util.stream.IntStream.range(0, subtarefas.size())
                .mapToObj(indice -> {
                    PipelineSubtarefaRequestDTO dto = subtarefas.get(indice);
                    PipelineVendasSubtarefa subtarefa = new PipelineVendasSubtarefa();
                    subtarefa.setTitulo(dto.titulo().trim());
                    subtarefa.setConcluida(dto.concluida());
                    subtarefa.setPosicao(dto.posicao() == null ? indice : dto.posicao());
                    return subtarefa;
                })
                .toList();
    }

    private boolean correspondeVisao(PipelineVendasTarefa tarefa, PipelineTarefaVisao visao, LocalDate hoje) {
        boolean aberta = tarefa.getStatus() != PipelineTarefaStatus.CONCLUIDA
                && tarefa.getStatus() != PipelineTarefaStatus.CANCELADA;
        return switch (visao) {
            case TODAS -> true;
            case HOJE -> aberta && (hoje.equals(tarefa.getPrazo()) || hoje.equals(tarefa.getDataInicio()));
            case ATRASADAS -> aberta && tarefa.getPrazo() != null && tarefa.getPrazo().isBefore(hoje);
            case FUTURAS -> aberta && dataDeReferencia(tarefa) != null && dataDeReferencia(tarefa).isAfter(hoje);
            case CONCLUIDAS -> tarefa.getStatus() == PipelineTarefaStatus.CONCLUIDA;
        };
    }

    private LocalDate dataDeReferencia(PipelineVendasTarefa tarefa) {
        return tarefa.getPrazo() != null ? tarefa.getPrazo() : tarefa.getDataInicio();
    }

    private void atualizarDataConclusao(PipelineVendasTarefa tarefa) {
        if (tarefa.getStatus() == PipelineTarefaStatus.CONCLUIDA && tarefa.getConcluidoEm() == null) {
            tarefa.setConcluidoEm(LocalDateTime.now());
        } else if (tarefa.getStatus() != PipelineTarefaStatus.CONCLUIDA) {
            tarefa.setConcluidoEm(null);
        }
    }

    private void registrarConclusaoSeNecessario(PipelineVendasTarefa tarefa,
                                                Long usuarioId,
                                                PipelineTarefaStatus statusAnterior) {
        if (statusAnterior != PipelineTarefaStatus.CONCLUIDA
                && tarefa.getStatus() == PipelineTarefaStatus.CONCLUIDA) {
            historicoService.registrar(tarefa.getNegocio(), usuarioId, PipelineHistoricoTipo.CONCLUSAO_TAREFA,
                    "Tarefa concluída: " + tarefa.getTitulo());
            cadenciaEngine.tarefaConcluida(tarefa);
        }
    }

    private void exigirPermissaoDeConclusaoSeNecessario(Long usuarioId,
                                                         PipelineTarefaStatus anterior,
                                                         PipelineTarefaStatus atual) {
        if (anterior != atual && (anterior == PipelineTarefaStatus.CONCLUIDA || atual == PipelineTarefaStatus.CONCLUIDA)) {
            exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR);
        }
    }

    private void validarDatas(LocalDate inicio, LocalDate prazo) {
        if (inicio != null && prazo != null && prazo.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O prazo não pode ser anterior à data de início");
        }
    }

    private PipelineVendasTarefa buscarTarefa(Long tarefaId) {
        return repository.findById(tarefaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa comercial não encontrada"));
    }

    private PipelineVendasNegocio buscarNegocio(Long negocioId) {
        return negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negócio não encontrado"));
    }

    private void exigirNegocioExistente(Long negocioId) {
        if (!negocioRepository.existsById(negocioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Negócio não encontrado");
        }
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado"));
    }

    private void exigirPermissao(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para tarefas comerciais");
        }
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private PipelineTarefaResponseDTO toResponse(PipelineVendasTarefa tarefa) {
        return new PipelineTarefaResponseDTO(
                tarefa.getIdTarefa(),
                tarefa.getNegocio().getIdNegocio(),
                tarefa.getNegocio().getNomeEmpresa(),
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getResponsavel().getId(),
                tarefa.getResponsavel().getNomeCompleto(),
                tarefa.getDataInicio(),
                tarefa.getPrazo(),
                tarefa.getPrioridade(),
                tarefa.getStatus(),
                tarefa.getTipo(),
                tarefa.getObservacoes(),
                tarefa.getSubtarefas().stream()
                        .map(subtarefa -> new PipelineSubtarefaResponseDTO(
                                subtarefa.getIdSubtarefa(),
                                subtarefa.getTitulo(),
                                subtarefa.isConcluida(),
                                subtarefa.getPosicao()
                        ))
                        .toList(),
                tarefa.getCriadoEm(),
                tarefa.getAtualizadoEm(),
                tarefa.getConcluidoEm()
        );
    }
}
