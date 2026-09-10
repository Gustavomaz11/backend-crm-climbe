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
import java.util.Set;

@Service
public class PipelineTarefaService {
    private final PipelineVendasTarefaRepository repository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PipelineHistoricoService historicoService;
    private final PipelineCadenciaEngine cadenciaEngine;
    private final RbacService rbacService;
    private final CargoHierarquiaAcessoService cargoHierarquiaAcessoService;

    public PipelineTarefaService(PipelineVendasTarefaRepository repository,
                                 PipelineVendasNegocioRepository negocioRepository,
                                 UsuarioRepository usuarioRepository,
                                 PipelineHistoricoService historicoService,
                                 PipelineCadenciaEngine cadenciaEngine,
                                 RbacService rbacService,
                                 CargoHierarquiaAcessoService cargoHierarquiaAcessoService) {
        this.repository = repository;
        this.negocioRepository = negocioRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoService = historicoService;
        this.cadenciaEngine = cadenciaEngine;
        this.rbacService = rbacService;
        this.cargoHierarquiaAcessoService = cargoHierarquiaAcessoService;
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
        boolean podeVisualizarTodas = podeVisualizarTodasAsTarefas(usuarioId);
        Set<Long> responsaveisVisiveis = podeVisualizarTodas
                ? Set.of(usuarioId)
                : cargoHierarquiaAcessoService.buscarUsuariosVisiveis(usuarioId);
        if (responsavelId != null) {
            if (!podeVisualizarTodas && !responsaveisVisiveis.contains(responsavelId)) {
                return List.of();
            }
            responsaveisVisiveis = Set.of(responsavelId);
        }
        boolean todosResponsaveis = podeVisualizarTodas && responsavelId == null;
        LocalDate hoje = LocalDate.now();
        return repository.findFiltradas(
                        visaoEfetiva == PipelineTarefaVisao.TODAS,
                        visaoEfetiva == PipelineTarefaVisao.HOJE,
                        visaoEfetiva == PipelineTarefaVisao.ATRASADAS,
                        visaoEfetiva == PipelineTarefaVisao.FUTURAS,
                        visaoEfetiva == PipelineTarefaVisao.CONCLUIDAS,
                        hoje, todosResponsaveis, responsaveisVisiveis, negocioId, funilId, normalizar(tipo)).stream()
                .distinct()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineTarefaResponseDTO> listarDoNegocio(Long negocioId, Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR);
        exigirNegocioExistente(negocioId);
        List<PipelineVendasTarefa> tarefas = podeVisualizarTodasAsTarefas(usuarioId)
                ? repository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(negocioId)
                : repository.findFiltradas(
                        true, false, false, false, false, LocalDate.now(), false,
                        cargoHierarquiaAcessoService.buscarUsuariosVisiveis(usuarioId), negocioId, null, null
                );
        return tarefas.stream()
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
        validarReaberturaAutomatica(tarefa, status);
        if (status == PipelineTarefaStatus.CANCELADA && tarefa.getMotivoCancelamento() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o motivo pela ação Cancelar tarefa");
        if (status != PipelineTarefaStatus.CANCELADA) { tarefa.setMotivoCancelamento(null); tarefa.setComentarioCancelamento(null); tarefa.setCanceladoEm(null); tarefa.setCanceladoPor(null); }
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

    @Transactional
    public PipelineTarefaResponseDTO cancelar(Long tarefaId, Long usuarioId, String motivo, String comentario) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR);
        if (motivo == null || !Set.of("SEM_CANAL_CONTATO", "DESNECESSARIA", "REPETIDA").contains(motivo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um motivo de cancelamento");
        if (comentario != null && comentario.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentário muito longo");
        PipelineVendasTarefa tarefa = buscarTarefa(tarefaId);
        if (tarefa.getStatus() == PipelineTarefaStatus.CANCELADA) return toResponse(tarefa);
        if (tarefa.getStatus() == PipelineTarefaStatus.CONCLUIDA) throw new ResponseStatusException(HttpStatus.CONFLICT, "Uma tarefa concluída precisa ser reaberta antes do cancelamento");
        tarefa.setMotivoCancelamento(motivo); tarefa.setComentarioCancelamento(normalizar(comentario));
        tarefa.setCanceladoEm(LocalDateTime.now()); tarefa.setCanceladoPor(usuarioId);
        return alterarStatus(tarefaId, usuarioId, PipelineTarefaStatus.CANCELADA);
    }

    private void aplicarDados(PipelineVendasTarefa tarefa, PipelineTarefaRequestDTO dto) {
        validarReaberturaAutomatica(tarefa, dto.status());
        tarefa.setTitulo(dto.titulo().trim());
        tarefa.setDescricao(normalizar(dto.descricao()));
        tarefa.setResponsavel(buscarUsuario(dto.responsavelId()));
        tarefa.setDataInicio(dto.dataInicio());
        tarefa.setPrazo(dto.prazo());
        tarefa.setPrioridade(dto.prioridade());
        if (dto.status() == PipelineTarefaStatus.CANCELADA && tarefa.getStatus() != PipelineTarefaStatus.CANCELADA) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o motivo pela ação Cancelar tarefa");
        if (tarefa.getStatus() == PipelineTarefaStatus.CANCELADA && dto.status() != tarefa.getStatus()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a ação de reabrir tarefa");
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

    private void validarReaberturaAutomatica(PipelineVendasTarefa tarefa, PipelineTarefaStatus novoStatus) {
        if (tarefa.getEtapaCadencia() != null && tarefa.getStatus() != novoStatus
                && (tarefa.getStatus() == PipelineTarefaStatus.CONCLUIDA || tarefa.getStatus() == PipelineTarefaStatus.CANCELADA))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Atividade automática encerrada. Crie uma tarefa manual ou reinicie a cadência");
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
        if (statusAnterior != tarefa.getStatus()
                && (tarefa.getStatus() == PipelineTarefaStatus.CONCLUIDA || tarefa.getStatus() == PipelineTarefaStatus.CANCELADA)) {
            historicoService.registrar(tarefa.getNegocio(), usuarioId, tarefa.getStatus() == PipelineTarefaStatus.CANCELADA ? PipelineHistoricoTipo.CANCELAMENTO_TAREFA : PipelineHistoricoTipo.CONCLUSAO_TAREFA,
                    (tarefa.getStatus() == PipelineTarefaStatus.CANCELADA ? "Tarefa cancelada (" + tarefa.getMotivoCancelamento() + "): " : "Tarefa concluída: ") + tarefa.getTitulo()
                            + (tarefa.getComentarioCancelamento() == null ? "" : " — " + tarefa.getComentarioCancelamento()));
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
        if (prazo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O prazo é obrigatório");
        }
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

    private boolean podeVisualizarTodasAsTarefas(Long usuarioId) {
        return rbacService.temPermissao(
                usuarioId,
                PermissaoCodigo.COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS
        );
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
                tarefa.getConcluidoEm(),
                tarefa.getCampanha() == null ? null : tarefa.getCampanha().getIdCampanha(),
                tarefa.getCampanha() == null ? null : tarefa.getCampanha().getNome(),
                tarefa.getNegocio().getNomeContato(), tarefa.getNegocio().getTelefone(), tarefa.getNegocio().getEmail(),
                tarefa.getMotivoCancelamento(), tarefa.getComentarioCancelamento(), tarefa.getCanceladoEm()
        );
    }
}
