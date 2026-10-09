package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoKanbanRaia;
import com.climb.api.model.ContratoKanbanSubtarefa;
import com.climb.api.model.ContratoKanbanTask;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContratoKanbanService {

    private final ContratoRepository contratoRepository;
    private final ContratoKanbanRaiaRepository raiaRepository;
    private final ContratoKanbanTaskRepository taskRepository;
    private final ContratoKanbanSubtarefaRepository subtarefaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContratoEquipeService equipeService;
    private final ContratoRateioTecnicoService rateioService;
    private final ContratoApoioAtuacaoService atuacaoService;
    private final TarefaResponsaveisService responsaveisService;

    public ContratoKanbanService(ContratoRepository contratoRepository,
                                 ContratoKanbanRaiaRepository raiaRepository,
                                 ContratoKanbanTaskRepository taskRepository,
                                 ContratoKanbanSubtarefaRepository subtarefaRepository,
                                 UsuarioRepository usuarioRepository,
                                 ContratoEquipeService equipeService,
                                 ContratoRateioTecnicoService rateioService,
                                 ContratoApoioAtuacaoService atuacaoService) {
        this.contratoRepository = contratoRepository;
        this.raiaRepository = raiaRepository;
        this.taskRepository = taskRepository;
        this.subtarefaRepository = subtarefaRepository;
        this.usuarioRepository = usuarioRepository;
        this.equipeService = equipeService;
        this.rateioService = rateioService;
        this.atuacaoService = atuacaoService;
        this.responsaveisService = new TarefaResponsaveisService(usuarioRepository);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanBoardResponseDTO buscarBoard(Long contratoId, Long usuarioId) {
        Contrato contrato = buscarContrato(contratoId);
        equipeService.exigirAcesso(contrato, usuarioId, false);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarRaia(Long contratoId, Long usuarioId, ContratoKanbanRaiaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = new ContratoKanbanRaia();
        raia.setContrato(contrato);
        raia.setTitulo(normalizarTitulo(dto.titulo(), "Título da raia é obrigatório"));
        raia.setConcluiTarefas(dto.concluiTarefas() != null ? dto.concluiTarefas() : tituloDeConclusao(raia.getTitulo()));
        raia.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoRaia(contratoId));
        raiaRepository.save(raia);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarRaia(Long contratoId, Long raiaId, Long usuarioId, ContratoKanbanRaiaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, raiaId);
        if (StringUtils.hasText(dto.titulo())) {
            raia.setTitulo(dto.titulo().trim());
        }
        if (dto.posicao() != null) {
            raia.setPosicao(dto.posicao());
        }
        if (dto.concluiTarefas() != null) raia.setConcluiTarefas(dto.concluiTarefas());
        raiaRepository.save(raia);
        taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId)
                .stream().filter(t -> Objects.equals(t.getRaia().getIdRaia(), raiaId)).forEach(t -> {
                    atuacaoService.sincronizar(t); rateioService.registrarAtuacao(t); taskRepository.save(t);
                });
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerRaia(Long contratoId, Long raiaId, Long usuarioId) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, raiaId);
        taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId).stream()
                .filter(t -> Objects.equals(t.getRaia().getIdRaia(), raiaId)).forEach(this::encerrarAtuacao);
        raiaRepository.delete(raia);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarTask(Long contratoId, Long usuarioId, ContratoKanbanTaskRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, dto.raiaId());

        ContratoKanbanTask task = new ContratoKanbanTask();
        task.setContrato(contrato);
        task.setRaia(raia);
        task.setTitulo(normalizarTitulo(dto.titulo(), "Título da tarefa é obrigatório"));
        task.setDescricao(normalizarTextoOpcional(dto.descricao()));
        task.setPrioridade(dto.prioridade() != null ? dto.prioridade() : ContratoKanbanPrioridade.MEDIA);
        task.setResponsaveis(responsaveisService.resolver(dto.responsavelIds(), dto.responsavelId(), false));
        task.setDataInicio(dto.dataInicio());
        task.setDataFim(dto.dataFim());
        task.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoTask(contratoId, raia.getIdRaia()));
        validarPeriodo(task);
        equipeService.atribuirResponsaveis(task, usuarioId, Set.of());
        taskRepository.save(task);
        atuacaoService.sincronizar(task);
        rateioService.registrarAtuacao(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarTask(Long contratoId, Long taskId, Long usuarioId, ContratoKanbanTaskRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        Set<Long> responsaveisAnteriores = task.getResponsaveisEfetivos().stream().map(Usuario::getId).collect(Collectors.toSet());

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
        task.setResponsaveis(responsaveisService.resolver(dto.responsavelIds(), dto.responsavelId(), false));
        subtarefaRepository.findByTask_IdTaskOrderByPosicaoAscIdSubtarefaAsc(taskId).forEach(subtarefa -> {
            if (subtarefa.getResponsavel() != null && task.getResponsaveisEfetivos().stream()
                    .noneMatch(usuario -> usuario.getId().equals(subtarefa.getResponsavel().getId()))) {
                subtarefa.setResponsavel(null);
                subtarefaRepository.save(subtarefa);
            }
        });
        task.setDataInicio(dto.dataInicio());
        task.setDataFim(dto.dataFim());
        if (dto.posicao() != null) {
            task.setPosicao(dto.posicao());
        }
        validarPeriodo(task);
        equipeService.atribuirResponsaveis(task, usuarioId, responsaveisAnteriores);
        taskRepository.save(task);
        atuacaoService.sincronizar(task);
        rateioService.registrarAtuacao(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO moverTask(Long contratoId, Long taskId, Long usuarioId, ContratoKanbanMoverTaskRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);

        ContratoKanbanRaia raia = buscarRaia(contratoId, dto != null ? dto.raiaId() : null);
        task.setRaia(raia);
        taskRepository.save(task);
        atuacaoService.sincronizar(task);
        rateioService.registrarAtuacao(task);
        return toBoardResponse(contrato, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerTask(Long contratoId, Long taskId, Long usuarioId) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        encerrarAtuacao(task);
        taskRepository.delete(task);
        return toBoardResponse(contrato, usuarioId);
    }

    private void encerrarAtuacao(ContratoKanbanTask task) {
        atuacaoService.encerrar(task);
        rateioService.registrarAtuacao(task);
        atuacaoService.desvincular(task);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarSubtarefa(Long contratoId,
                                                         Long taskId,
                                                         Long usuarioId,
                                                         ContratoKanbanSubtarefaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        exigirSubtarefaRequest(dto);

        ContratoKanbanSubtarefa subtarefa = new ContratoKanbanSubtarefa();
        subtarefa.setTask(task);
        subtarefa.setResponsavel(TarefaResponsaveisService.daSubtarefa(dto.responsavelId(), task.getResponsaveisEfetivos()));
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
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        exigirSubtarefaRequest(dto);
        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);
        subtarefa.setResponsavel(TarefaResponsaveisService.daSubtarefa(dto.responsavelId(), task.getResponsaveisEfetivos()));

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
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
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
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        buscarTask(contratoId, taskId);
        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);
        subtarefaRepository.delete(subtarefa);
        return toBoardResponse(contrato, usuarioId);
    }

    private ContratoKanbanBoardResponseDTO toBoardResponse(Contrato contrato, Long usuarioId) {
        List<ContratoKanbanRaia> raias = raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(contrato.getIdContrato());
        List<ContratoKanbanTask> tasks = taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contrato.getIdContrato());
        Set<Long> taskIds = tasks.stream().map(ContratoKanbanTask::getIdTask).collect(Collectors.toSet());
        List<ContratoKanbanSubtarefa> subtarefas = taskIds.isEmpty()
                ? List.of()
                : subtarefaRepository.findByTask_IdTaskInOrderByTask_IdTaskAscPosicaoAscIdSubtarefaAsc(taskIds);
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

        List<Usuario> equipe = equipeService.membrosAtuais(contrato);
        List<UsuarioResumoDTO> participantes = equipe.stream()
                .sorted(Comparator.comparing(Usuario::getNomeCompleto, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toUsuarioResumo)
                .toList();
        List<UsuarioResumoDTO> usuariosDisponiveis = (isGestor(contrato, usuarioId)
                ? usuarioRepository.findAllBySituacaoOrderByNomeCompletoAsc("ATIVO") : equipe).stream()
                .map(this::toUsuarioResumo)
                .toList();

        return new ContratoKanbanBoardResponseDTO(
                contrato.getIdContrato(),
                contrato.getUrlPdf(),
                isGestor(contrato, usuarioId),
                equipeService.podeEditar(contrato, usuarioId),
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
                        .toList(),
                raia.isConcluiTarefas()
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
                subtarefas.stream().map(this::toSubtarefaResponse).toList(),
                task.getResponsaveisEfetivos().stream().map(this::toUsuarioResumo).toList()
        );
    }

    private ContratoKanbanSubtarefaResponseDTO toSubtarefaResponse(ContratoKanbanSubtarefa subtarefa) {
        return new ContratoKanbanSubtarefaResponseDTO(
                subtarefa.getIdSubtarefa(),
                subtarefa.getTitulo(),
                subtarefa.isConcluida(),
                subtarefa.getPosicao(),
                subtarefa.getCriadoEm(),
                subtarefa.getAtualizadoEm(),
                toUsuarioResumo(subtarefa.getResponsavel())
        );
    }

    private UsuarioResumoDTO toUsuarioResumo(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail());
    }

    private Contrato exigirEdicaoContrato(Long contratoId, Long usuarioId) {
        Contrato contrato = buscarContrato(contratoId);
        equipeService.exigirAcesso(contrato, usuarioId, true);
        return contrato;
    }
    private boolean tituloDeConclusao(String titulo) {
        String semAcentos = java.text.Normalizer.normalize(titulo, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.trim().toLowerCase(java.util.Locale.ROOT).matches("concluid[oa]s?");
    }

    private boolean isGestor(Contrato contrato, Long usuarioId) {
        return contrato.getResponsavel() != null
                && contrato.getResponsavel().getId() != null
                && Objects.equals(contrato.getResponsavel().getId(), usuarioId);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanTask exigirTaskVisivel(Long taskId, Long usuarioId) {
        ContratoKanbanTask task = taskRepository.findById(taskId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada."));
        equipeService.exigirAcesso(task.getContrato(), usuarioId, false);
        return task;
    }

    private Contrato buscarContrato(Long contratoId) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado"));
        if (contrato.getEtapaPreparacao() != null && !ContratoService.STATUS_APROVADO.equals(contrato.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este contrato ainda está em preparação na aba Contratos");
        }
        return contrato;
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
