package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoKanbanRaia;
import com.climb.api.model.ContratoKanbanSubtarefa;
import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.ContratoKanbanPrioridade;
import com.climb.api.model.dto.ContratoKanbanBoardResponseDTO;
import com.climb.api.model.dto.ContratoKanbanMoverTaskRequestDTO;
import com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO;
import com.climb.api.model.dto.ContratoKanbanRaiaResponseDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaConclusaoRequestDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaRequestDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaResponseDTO;
import com.climb.api.model.dto.ContratoKanbanTaskRequestDTO;
import com.climb.api.model.dto.ContratoKanbanTaskResponseDTO;
import com.climb.api.model.dto.UsuarioResumoDTO;
import com.climb.api.repository.ContratoKanbanRaiaRepository;
import com.climb.api.repository.ContratoKanbanSubtarefaRepository;
import com.climb.api.repository.ContratoKanbanTaskRepository;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ContratoKanbanService {

    private final ContratoRepository contratoRepository;
    private final ContratoKanbanRaiaRepository raiaRepository;
    private final ContratoKanbanTaskRepository taskRepository;
    private final ContratoKanbanSubtarefaRepository subtarefaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RbacService rbacService;

    public ContratoKanbanService(ContratoRepository contratoRepository,
                                 ContratoKanbanRaiaRepository raiaRepository,
                                 ContratoKanbanTaskRepository taskRepository,
                                 ContratoKanbanSubtarefaRepository subtarefaRepository,
                                 UsuarioRepository usuarioRepository,
                                 RbacService rbacService) {
        this.contratoRepository = contratoRepository;
        this.raiaRepository = raiaRepository;
        this.taskRepository = taskRepository;
        this.subtarefaRepository = subtarefaRepository;
        this.usuarioRepository = usuarioRepository;
        this.rbacService = rbacService;
    }

    public ContratoKanbanBoardResponseDTO buscarBoard(Long contratoId, Long usuarioId) {
        exigirPermissaoKanban(usuarioId);
        Contrato contrato = buscarContrato(contratoId);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarRaia(Long contratoId, Long usuarioId, ContratoKanbanRaiaRequestDTO dto) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = new ContratoKanbanRaia();
        raia.setContrato(contrato);
        raia.setTitulo(normalizarTitulo(dto.titulo(), "Título da raia é obrigatório"));
        raia.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoRaia(contratoId));
        raiaRepository.save(raia);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarRaia(Long contratoId, Long raiaId, Long usuarioId, ContratoKanbanRaiaRequestDTO dto) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, raiaId);
        if (StringUtils.hasText(dto.titulo())) {
            raia.setTitulo(dto.titulo().trim());
        }
        if (dto.posicao() != null) {
            raia.setPosicao(dto.posicao());
        }
        raiaRepository.save(raia);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerRaia(Long contratoId, Long raiaId, Long usuarioId) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, raiaId);
        raiaRepository.delete(raia);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarTask(Long contratoId, Long usuarioId, ContratoKanbanTaskRequestDTO dto) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, dto.raiaId());

        ContratoKanbanTask task = new ContratoKanbanTask();
        task.setContrato(contrato);
        task.setRaia(raia);
        task.setTitulo(normalizarTitulo(dto.titulo(), "Título da tarefa é obrigatório"));
        task.setDescricao(normalizarTextoOpcional(dto.descricao()));
        task.setPrioridade(dto.prioridade() != null ? dto.prioridade() : ContratoKanbanPrioridade.MEDIA);
        task.setResponsavel(buscarResponsavelDaTask(dto.responsavelId()));
        task.setDataInicio(dto.dataInicio());
        task.setDataFim(dto.dataFim());
        task.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoTask(contratoId, raia.getIdRaia()));
        validarPeriodo(task);
        taskRepository.save(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarTask(Long contratoId, Long taskId, Long usuarioId, ContratoKanbanTaskRequestDTO dto) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);

        if (dto.raiaId() != null && !Objects.equals(task.getRaia().getIdRaia(), dto.raiaId())) {
            task.setRaia(buscarRaia(contratoId, dto.raiaId()));
        }
        if (StringUtils.hasText(dto.titulo())) {
            task.setTitulo(dto.titulo().trim());
        }
        task.setDescricao(normalizarTextoOpcional(dto.descricao()));
        if (dto.prioridade() != null) {
            task.setPrioridade(dto.prioridade());
        }
        task.setResponsavel(buscarResponsavelDaTask(dto.responsavelId()));
        task.setDataInicio(dto.dataInicio());
        task.setDataFim(dto.dataFim());
        if (dto.posicao() != null) {
            task.setPosicao(dto.posicao());
        }
        validarPeriodo(task);
        taskRepository.save(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO moverTask(Long contratoId, Long taskId, Long usuarioId, ContratoKanbanMoverTaskRequestDTO dto) {
        exigirPermissaoKanban(usuarioId);
        Contrato contrato = buscarContrato(contratoId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);

        if (!isGestor(contrato, usuarioId) && !isResponsavelTask(task, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o gestor do contrato ou o responsável da tarefa pode mover o card");
        }

        ContratoKanbanRaia raia = buscarRaia(contratoId, dto != null ? dto.raiaId() : null);
        task.setRaia(raia);
        taskRepository.save(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerTask(Long contratoId, Long taskId, Long usuarioId) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        taskRepository.delete(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarSubtarefa(Long contratoId,
                                                         Long taskId,
                                                         Long usuarioId,
                                                         ContratoKanbanSubtarefaRequestDTO dto) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        exigirSubtarefaRequest(dto);

        ContratoKanbanSubtarefa subtarefa = new ContratoKanbanSubtarefa();
        subtarefa.setTask(task);
        subtarefa.setTitulo(normalizarTitulo(dto.titulo(), "Título da subtarefa é obrigatório"));
        subtarefa.setConcluida(Boolean.TRUE.equals(dto.concluida()));
        subtarefa.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoSubtarefa(taskId));
        subtarefaRepository.save(subtarefa);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarSubtarefa(Long contratoId,
                                                             Long taskId,
                                                             Long subtarefaId,
                                                             Long usuarioId,
                                                             ContratoKanbanSubtarefaRequestDTO dto) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        buscarTask(contratoId, taskId);
        exigirSubtarefaRequest(dto);
        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);

        if (StringUtils.hasText(dto.titulo())) {
            subtarefa.setTitulo(dto.titulo().trim());
        }
        if (dto.concluida() != null) {
            subtarefa.setConcluida(dto.concluida());
        }
        if (dto.posicao() != null) {
            subtarefa.setPosicao(dto.posicao());
        }
        subtarefaRepository.save(subtarefa);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarConclusaoSubtarefa(Long contratoId,
                                                                      Long taskId,
                                                                      Long subtarefaId,
                                                                      Long usuarioId,
                                                                      ContratoKanbanSubtarefaConclusaoRequestDTO dto) {
        exigirPermissaoKanban(usuarioId);
        Contrato contrato = buscarContrato(contratoId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        if (!isGestor(contrato, usuarioId) && !isResponsavelTask(task, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o gestor ou o responsável da tarefa pode concluir subtarefas");
        }
        if (dto == null || dto.concluida() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação da subtarefa é obrigatória");
        }

        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);
        subtarefa.setConcluida(dto.concluida());
        subtarefaRepository.save(subtarefa);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerSubtarefa(Long contratoId,
                                                           Long taskId,
                                                           Long subtarefaId,
                                                           Long usuarioId) {
        Contrato contrato = exigirGestorContrato(contratoId, usuarioId);
        buscarTask(contratoId, taskId);
        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);
        subtarefaRepository.delete(subtarefa);
        return toBoardResponse(contrato, usuarioId);
    }

    private ContratoKanbanBoardResponseDTO toBoardResponse(Contrato contrato, Long usuarioId) {
        List<ContratoKanbanRaia> raias = raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(contrato.getIdContrato());
        List<ContratoKanbanTask> tasks = taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contrato.getIdContrato());
        List<ContratoKanbanSubtarefa> subtarefas = subtarefaRepository
                .findByTask_Contrato_IdContratoOrderByTask_IdTaskAscPosicaoAscIdSubtarefaAsc(contrato.getIdContrato());
        Map<Long, List<ContratoKanbanTask>> tasksPorRaia = tasks.stream()
                .collect(Collectors.groupingBy(task -> task.getRaia().getIdRaia()));
        Map<Long, List<ContratoKanbanSubtarefa>> subtarefasPorTask = subtarefas.stream()
                .collect(Collectors.groupingBy(subtarefa -> subtarefa.getTask().getIdTask()));

        List<ContratoKanbanRaiaResponseDTO> raiasDto = raias.stream()
                .map(raia -> toRaiaResponse(
                        raia,
                        tasksPorRaia.getOrDefault(raia.getIdRaia(), List.of()),
                        subtarefasPorTask
                ))
                .toList();

        List<UsuarioResumoDTO> participantes = contrato.getParticipantes().stream()
                .sorted(Comparator.comparing(Usuario::getNomeCompleto, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toUsuarioResumo)
                .toList();
        List<UsuarioResumoDTO> usuariosDisponiveis = usuarioRepository
                .findAllBySituacaoOrderByNomeCompletoAsc("ATIVO")
                .stream()
                .map(this::toUsuarioResumo)
                .toList();

        return new ContratoKanbanBoardResponseDTO(
                contrato.getIdContrato(),
                contrato.getUrlPdf(),
                isGestor(contrato, usuarioId),
                toUsuarioResumo(contrato.getResponsavel()),
                participantes,
                usuariosDisponiveis,
                raiasDto
        );
    }

    private ContratoKanbanRaiaResponseDTO toRaiaResponse(ContratoKanbanRaia raia,
                                                         List<ContratoKanbanTask> tasks,
                                                         Map<Long, List<ContratoKanbanSubtarefa>> subtarefasPorTask) {
        return new ContratoKanbanRaiaResponseDTO(
                raia.getIdRaia(),
                raia.getTitulo(),
                raia.getPosicao(),
                raia.getCriadoEm(),
                raia.getAtualizadoEm(),
                tasks.stream()
                        .map(task -> toTaskResponse(
                                task,
                                subtarefasPorTask.getOrDefault(task.getIdTask(), List.of())
                        ))
                        .toList()
        );
    }

    private ContratoKanbanTaskResponseDTO toTaskResponse(ContratoKanbanTask task,
                                                         List<ContratoKanbanSubtarefa> subtarefas) {
        return new ContratoKanbanTaskResponseDTO(
                task.getIdTask(),
                task.getRaia().getIdRaia(),
                task.getTitulo(),
                task.getDescricao(),
                task.getPrioridade(),
                toUsuarioResumo(task.getResponsavel()),
                task.getDataInicio(),
                task.getDataFim(),
                task.getPosicao(),
                task.getCriadoEm(),
                task.getAtualizadoEm(),
                subtarefas.stream().map(this::toSubtarefaResponse).toList()
        );
    }

    private ContratoKanbanSubtarefaResponseDTO toSubtarefaResponse(ContratoKanbanSubtarefa subtarefa) {
        return new ContratoKanbanSubtarefaResponseDTO(
                subtarefa.getIdSubtarefa(),
                subtarefa.getTitulo(),
                subtarefa.isConcluida(),
                subtarefa.getPosicao(),
                subtarefa.getCriadoEm(),
                subtarefa.getAtualizadoEm()
        );
    }

    private UsuarioResumoDTO toUsuarioResumo(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail());
    }

    private Contrato exigirGestorContrato(Long contratoId, Long usuarioId) {
        exigirPermissaoKanban(usuarioId);
        Contrato contrato = buscarContrato(contratoId);
        if (!isGestor(contrato, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o gestor do contrato pode alterar o Kanban");
        }
        return contrato;
    }

    private void exigirPermissaoKanban(Long usuarioId) {
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
        if (!rbacService.temPermissao(usuarioId, PermissaoCodigo.CONTRATO_KANBAN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não tem permissão para visualizar o Kanban de contratos");
        }
    }

    private boolean isGestor(Contrato contrato, Long usuarioId) {
        return contrato.getResponsavel() != null
                && contrato.getResponsavel().getId() != null
                && Objects.equals(contrato.getResponsavel().getId(), usuarioId);
    }

    private boolean isResponsavelTask(ContratoKanbanTask task, Long usuarioId) {
        return task.getResponsavel() != null
                && task.getResponsavel().getId() != null
                && Objects.equals(task.getResponsavel().getId(), usuarioId);
    }

    private Contrato buscarContrato(Long contratoId) {
        return contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado"));
    }

    private ContratoKanbanRaia buscarRaia(Long contratoId, Long raiaId) {
        if (raiaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Raia é obrigatória");
        }
        return raiaRepository.findByIdRaiaAndContrato_IdContrato(raiaId, contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Raia não encontrada"));
    }

    private ContratoKanbanTask buscarTask(Long contratoId, Long taskId) {
        return taskRepository.findByIdTaskAndContrato_IdContrato(taskId, contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada"));
    }

    private Usuario buscarResponsavelDaTask(Long responsavelId) {
        if (responsavelId == null) {
            return null;
        }
        Usuario usuario = usuarioRepository.findById(responsavelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável da tarefa não encontrado"));
        if (!"ATIVO".equals(usuario.getSituacao())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável da tarefa precisa ser um usuário ativo");
        }
        return usuario;
    }

    private ContratoKanbanSubtarefa buscarSubtarefa(Long contratoId, Long taskId, Long subtarefaId) {
        return subtarefaRepository
                .findByIdSubtarefaAndTask_IdTaskAndTask_Contrato_IdContrato(subtarefaId, taskId, contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtarefa não encontrada"));
    }

    private void exigirSubtarefaRequest(ContratoKanbanSubtarefaRequestDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados da subtarefa são obrigatórios");
        }
    }

    private void validarPeriodo(ContratoKanbanTask task) {
        if (task.getDataInicio() != null && task.getDataFim() != null && task.getDataFim().isBefore(task.getDataInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data fim não pode ser anterior à data de início");
        }
    }

    private String normalizarTitulo(String titulo, String mensagemErro) {
        if (!StringUtils.hasText(titulo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagemErro);
        }
        return titulo.trim();
    }

    private String normalizarTextoOpcional(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }
        return texto.trim();
    }

    private int proximaPosicaoRaia(Long contratoId) {
        return raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(contratoId)
                .stream()
                .map(ContratoKanbanRaia::getPosicao)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(posicao -> posicao + 1)
                .orElse(0);
    }

    private int proximaPosicaoTask(Long contratoId, Long raiaId) {
        return taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId)
                .stream()
                .filter(task -> Objects.equals(task.getRaia().getIdRaia(), raiaId))
                .map(ContratoKanbanTask::getPosicao)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(posicao -> posicao + 1)
                .orElse(0);
    }

    private int proximaPosicaoSubtarefa(Long taskId) {
        return subtarefaRepository.findByTask_IdTaskOrderByPosicaoAscIdSubtarefaAsc(taskId)
                .stream()
                .map(ContratoKanbanSubtarefa::getPosicao)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(posicao -> posicao + 1)
                .orElse(0);
    }
}
