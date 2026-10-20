package com.climb.api.service;

import java.time.LocalDateTime;
import com.climb.api.model.dto.KanbanItemArquivadoDTO;
import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoServicos;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.model.ContratoKanbanRaia;
import com.climb.api.model.ContratoKanbanSubtarefa;
import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.ContratoKanbanPrioridade;
import com.climb.api.model.dto.ContratoKanbanBoardResponseDTO;
import com.climb.api.model.dto.ContratoKanbanAberturaResponseDTO;
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
    private final TarefaNotificacaoService notificacoes;
    private final org.springframework.context.ApplicationEventPublisher eventos;

    public ContratoKanbanService(ContratoRepository contratoRepository,
                                 ContratoKanbanRaiaRepository raiaRepository,
                                 ContratoKanbanTaskRepository taskRepository,
                                 ContratoKanbanSubtarefaRepository subtarefaRepository,
                                 UsuarioRepository usuarioRepository,
                                 ContratoEquipeService equipeService,
                                 ContratoRateioTecnicoService rateioService,
                                 ContratoApoioAtuacaoService atuacaoService,
                                 TarefaNotificacaoService notificacoes,
                                 org.springframework.context.ApplicationEventPublisher eventos) {
        this.contratoRepository = contratoRepository;
        this.raiaRepository = raiaRepository;
        this.taskRepository = taskRepository;
        this.subtarefaRepository = subtarefaRepository;
        this.usuarioRepository = usuarioRepository;
        this.equipeService = equipeService;
        this.rateioService = rateioService;
        this.atuacaoService = atuacaoService;
        this.notificacoes = notificacoes;
        this.eventos = eventos;
        this.responsaveisService = new TarefaResponsaveisService(usuarioRepository);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanBoardResponseDTO buscarBoard(Long contratoId, Long usuarioId) {
        return buscarBoard(contratoId, usuarioId, null);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanBoardResponseDTO buscarBoard(Long contratoId, Long usuarioId, ServicoComercial servico) {
        Contrato contrato = buscarContrato(contratoId);
        var selecionado = resolverServico(contrato, servico);
        equipeService.exigirAcesso(contrato, usuarioId, false, selecionado);
        return toBoardResponse(contrato, usuarioId, selecionado);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanAberturaResponseDTO buscarAbertura(Long contratoId, Long usuarioId) {
        return buscarAbertura(contratoId, usuarioId, null);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanAberturaResponseDTO buscarAbertura(Long contratoId, Long usuarioId, ServicoComercial servico) {
        Contrato contrato = buscarContrato(contratoId);
        var selecionado = resolverServico(contrato, servico);
        var equipe = equipeService.buscarEquipe(contrato, usuarioId, selecionado);
        return new ContratoKanbanAberturaResponseDTO(equipe,
                equipe.configurada() ? toBoardResponse(contrato, usuarioId, selecionado) : null,
                isGestor(contrato, usuarioId, selecionado));
    }

    @Transactional(readOnly = true)
    public List<KanbanItemArquivadoDTO> listarArquivados(Long contratoId, Long usuarioId) {
        return listarArquivados(contratoId, usuarioId, null);
    }

    @Transactional(readOnly = true)
    public List<KanbanItemArquivadoDTO> listarArquivados(Long contratoId, Long usuarioId, ServicoComercial servico) {
        Contrato contrato = buscarContrato(contratoId);
        var selecionado = resolverServico(contrato, servico);
        equipeService.exigirAcesso(contrato, usuarioId, false, selecionado);
        return taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId).stream()
                .filter(task -> Objects.equals(ContratoServicos.daTarefa(task), selecionado))
                .filter(ContratoKanbanTask::estaArquivado)
                .map(task -> new KanbanItemArquivadoDTO(task.getIdTask(), task.getTitulo(),
                        task.getRaia().getTitulo(), task.getResponsaveisEfetivos().stream().map(Usuario::getNomeCompleto)
                        .collect(Collectors.joining(", ")), task.getArquivadoEm(),
                        toTaskResponse(task, subtarefaRepository.findByTask_IdTaskOrderByPosicaoAscIdSubtarefaAsc(task.getIdTask()))))
                .toList();
    }

    @Transactional
    public void alterarArquivamento(Long contratoId, Long taskId, Long usuarioId, boolean arquivado) {
        exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
        task.alterarArquivamento(arquivado, LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")));
        taskRepository.save(task);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarRaia(Long contratoId, Long usuarioId, ContratoKanbanRaiaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        equipeService.exigirAcesso(contrato, usuarioId, true, resolverServico(contrato, dto.servico()));
        ContratoKanbanRaia raia = new ContratoKanbanRaia();
        raia.setContrato(contrato);
        raia.setServico(resolverServico(contrato, dto.servico()));
        raia.setTitulo(normalizarTitulo(dto.titulo(), "Título da raia é obrigatório"));
        raia.setConcluiTarefas(dto.concluiTarefas() != null ? dto.concluiTarefas() : tituloDeConclusao(raia.getTitulo()));
        raia.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoRaia(contratoId, raia.getServico()));
        raiaRepository.save(raia);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daRaia(raia));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarRaia(Long contratoId, Long raiaId, Long usuarioId, ContratoKanbanRaiaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, raiaId);
        equipeService.exigirAcesso(raia.getContrato(), usuarioId, true, ContratoServicos.daRaia(raia));
        String estadoAnterior = TarefaNotificacaoService.estado(raia);
        if (StringUtils.hasText(dto.titulo())) {
            raia.setTitulo(dto.titulo().trim());
        }
        if (dto.posicao() != null) {
            raia.setPosicao(dto.posicao());
        }
        if (Boolean.TRUE.equals(dto.concluiTarefas()) && !raia.isConcluiTarefas()) {
            taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId)
                    .stream().filter(t -> Objects.equals(t.getRaia().getIdRaia(), raiaId))
                    .forEach(t -> TarefaPrazoPolicy.justificarConclusao(t.getDataFim(), null, TarefaPrazoPolicy.hoje()));
        }
        if (dto.concluiTarefas() != null) raia.setConcluiTarefas(dto.concluiTarefas());
        raiaRepository.save(raia);
        taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId)
                .stream().filter(t -> Objects.equals(t.getRaia().getIdRaia(), raiaId)).forEach(t -> {
                    atuacaoService.sincronizar(t); rateioService.registrarAtuacao(t); taskRepository.save(t);
                    notificacoes.contratoMovida(t, estadoAnterior);
                });
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daRaia(raia));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerRaia(Long contratoId, Long raiaId, Long usuarioId) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, raiaId);
        equipeService.exigirAcesso(raia.getContrato(), usuarioId, true, ContratoServicos.daRaia(raia));
        if (taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId).stream()
                .anyMatch(task -> task.estaArquivado() && Objects.equals(task.getRaia().getIdRaia(), raiaId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta coluna contém tarefas arquivadas. Desarquive e mova essas tarefas antes de excluir a coluna.");
        }
        taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contratoId).stream()
                .filter(t -> Objects.equals(t.getRaia().getIdRaia(), raiaId)).forEach(this::encerrarAtuacao);
        raiaRepository.delete(raia);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daRaia(raia));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarTask(Long contratoId, Long usuarioId, ContratoKanbanTaskRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanRaia raia = buscarRaia(contratoId, dto.raiaId());
        equipeService.exigirAcesso(raia.getContrato(), usuarioId, true, ContratoServicos.daRaia(raia));

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
        if (task.getDataFim() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Informe o prazo para concluir esta tarefa.");
        validarPeriodo(task);
        justificarConclusao(task, raia, true, dto.justificativaAtraso(), usuarioId);
        equipeService.atribuirResponsaveis(task, usuarioId, Set.of());
        taskRepository.save(task);
        notificacoes.atribuicoes(task, Set.of());
        atuacaoService.sincronizar(task);
        rateioService.registrarAtuacao(task);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarTask(Long contratoId, Long taskId, Long usuarioId, ContratoKanbanTaskRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
        Set<Long> responsaveisAnteriores = task.getResponsaveisEfetivos().stream().map(Usuario::getId).collect(Collectors.toSet());
        String estadoAnterior = TarefaNotificacaoService.estado(task.getRaia());

        if (!Objects.equals(task.getDataFim(), dto.dataFim())
                && !isGestor(contrato, usuarioId, ContratoServicos.daTarefa(task))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente o responsável técnico do serviço pode alterar o prazo da tarefa.");
        }
        if (task.getDataFim() != null && dto.dataFim() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "O prazo da tarefa precisa continuar preenchido.");
        ContratoKanbanRaia destino = dto.raiaId() == null || Objects.equals(dto.raiaId(), task.getRaia().getIdRaia())
                ? task.getRaia() : buscarRaia(contratoId, dto.raiaId());
        exigirMesmoServico(task, destino);
        justificarConclusao(task, destino, !task.getRaia().isConcluiTarefas(), dto.justificativaAtraso(), usuarioId);

        if (dto.raiaId() != null && !Objects.equals(task.getRaia().getIdRaia(), dto.raiaId())) {
            task.setRaia(destino);
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
        notificacoes.atribuicoes(task, responsaveisAnteriores);
        notificacoes.contratoMovida(task, estadoAnterior);
        atuacaoService.sincronizar(task);
        rateioService.registrarAtuacao(task);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO moverTask(Long contratoId, Long taskId, Long usuarioId, ContratoKanbanMoverTaskRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));

        ContratoKanbanRaia raia = buscarRaia(contratoId, dto != null ? dto.raiaId() : null);
        equipeService.exigirAcesso(raia.getContrato(), usuarioId, true, ContratoServicos.daRaia(raia));
        exigirMesmoServico(task, raia);
        String estadoAnterior = TarefaNotificacaoService.estado(task.getRaia());
        justificarConclusao(task, raia, !task.getRaia().isConcluiTarefas(), dto.justificativaAtraso(), usuarioId);
        task.setRaia(raia);
        taskRepository.save(task);
        notificacoes.contratoMovida(task, estadoAnterior);
        atuacaoService.sincronizar(task);
        rateioService.registrarAtuacao(task);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerTask(Long contratoId, Long taskId, Long usuarioId) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
        encerrarAtuacao(task);
        taskRepository.delete(task);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    private void encerrarAtuacao(ContratoKanbanTask task) {
        atuacaoService.encerrar(task);
        rateioService.registrarAtuacao(task);
        atuacaoService.desvincular(task);
    }

    private void justificarConclusao(ContratoKanbanTask task, ContratoKanbanRaia destino, boolean entrando,
                                     String justificativa, Long usuarioId) {
        if (!entrando || !destino.isConcluiTarefas()) return;
        String texto = TarefaPrazoPolicy.justificarConclusao(task.getDataFim(), justificativa, TarefaPrazoPolicy.hoje());
        if (texto != null) task.justificarAtraso(texto, usuarioId);
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO criarSubtarefa(Long contratoId,
                                                         Long taskId,
                                                         Long usuarioId,
                                                         ContratoKanbanSubtarefaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
        exigirSubtarefaRequest(dto);

        ContratoKanbanSubtarefa subtarefa = new ContratoKanbanSubtarefa();
        subtarefa.setTask(task);
        subtarefa.setResponsavel(TarefaResponsaveisService.daSubtarefa(dto.responsavelId(), task.getResponsaveisEfetivos()));
        subtarefa.setTitulo(normalizarTitulo(dto.titulo(), "Título da subtarefa é obrigatório"));
        subtarefa.setConcluida(Boolean.TRUE.equals(dto.concluida()));
        subtarefa.setPosicao(dto.posicao() != null ? dto.posicao() : proximaPosicaoSubtarefa(taskId));
        subtarefaRepository.save(subtarefa);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarSubtarefa(Long contratoId,
                                                             Long taskId,
                                                             Long subtarefaId,
                                                             Long usuarioId,
                                                             ContratoKanbanSubtarefaRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
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
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO atualizarConclusaoSubtarefa(Long contratoId,
                                                                      Long taskId,
                                                                      Long subtarefaId,
                                                                      Long usuarioId,
                                                                      ContratoKanbanSubtarefaConclusaoRequestDTO dto) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
        if (dto == null || dto.concluida() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Situação da subtarefa é obrigatória");
        }

        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);
        subtarefa.setConcluida(dto.concluida());
        subtarefaRepository.save(subtarefa);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    @Transactional
    public ContratoKanbanBoardResponseDTO removerSubtarefa(Long contratoId,
                                                           Long taskId,
                                                           Long subtarefaId,
                                                           Long usuarioId) {
        Contrato contrato = exigirEdicaoContrato(contratoId, usuarioId);
        ContratoKanbanTask task = buscarTask(contratoId, taskId);
        equipeService.exigirAcesso(task.getContrato(), usuarioId, true, ContratoServicos.daTarefa(task));
        ContratoKanbanSubtarefa subtarefa = buscarSubtarefa(contratoId, taskId, subtarefaId);
        subtarefaRepository.delete(subtarefa);
        return toBoardResponse(contrato, usuarioId, ContratoServicos.daTarefa(task));
    }

    private ContratoKanbanBoardResponseDTO toBoardResponse(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        List<ContratoKanbanRaia> raias = raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(contrato.getIdContrato())
                .stream().filter(raia -> Objects.equals(ContratoServicos.daRaia(raia), servico)).toList();
        List<ContratoKanbanTask> tasks = taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contrato.getIdContrato())
                .stream().filter(task -> !task.estaArquivado() && Objects.equals(ContratoServicos.daTarefa(task), servico)).toList();
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

        List<Usuario> equipe = equipeService.membrosAtuais(contrato, servico);
        List<UsuarioResumoDTO> participantes = equipe.stream()
                .sorted(Comparator.comparing(Usuario::getNomeCompleto, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toUsuarioResumo)
                .toList();
        List<UsuarioResumoDTO> usuariosDisponiveis = (isGestor(contrato, usuarioId, servico)
                ? usuarioRepository.findAllBySituacaoOrderByNomeCompletoAsc("ATIVO") : equipe).stream()
                .map(this::toUsuarioResumo)
                .toList();

        return new ContratoKanbanBoardResponseDTO(
                contrato.getIdContrato(),
                contrato.getUrlPdf(),
                isGestor(contrato, usuarioId, servico),
                equipeService.podeEditar(contrato, usuarioId, servico),
                toUsuarioResumo(ContratoServicos.responsavel(contrato, servico)),
                participantes,
                usuariosDisponiveis,
                raiasDto,
                servico
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
                task.getResponsaveisEfetivos().stream().map(this::toUsuarioResumo).toList(),
                task.getJustificativaAtraso()
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
        if (usuarioId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        Contrato contrato = contratoRepository.findByIdForUpdate(contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado"));
        exigirContratoAprovado(contrato);
        // A autorização é verificada no serviço da raia/tarefa antes de qualquer alteração.
        if (contrato.getEmpresa() != null)
            eventos.publishEvent(new DrivePermissoesAlteradas(contrato.getEmpresa().getIdEmpresa()));
        return contrato;
    }
    private boolean tituloDeConclusao(String titulo) {
        String semAcentos = java.text.Normalizer.normalize(titulo, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.trim().toLowerCase(java.util.Locale.ROOT).matches("concluid[oa]s?");
    }

    private boolean isGestor(Contrato contrato, Long usuarioId, ServicoComercial servico) {
        Usuario lider = ContratoServicos.responsavel(contrato, servico);
        return usuarioId != null && lider != null && Objects.equals(lider.getId(), usuarioId);
    }

    @Transactional(readOnly = true)
    public ContratoKanbanTask exigirTaskVisivel(Long taskId, Long usuarioId) {
        ContratoKanbanTask task = taskRepository.findById(taskId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada."));
        equipeService.exigirAcesso(task.getContrato(), usuarioId, false, ContratoServicos.daTarefa(task));
        exigirServicoAtivo(task.getContrato(), ContratoServicos.daTarefa(task));
        return task;
    }

    private Contrato buscarContrato(Long contratoId) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado"));
        exigirContratoAprovado(contrato);
        return contrato;
    }

    private void exigirContratoAprovado(Contrato contrato) {
        if (contrato.getEtapaPreparacao() != null && !ContratoService.STATUS_APROVADO.equals(contrato.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este contrato ainda está em preparação na aba Contratos");
        }
    }

    private ContratoKanbanRaia buscarRaia(Long contratoId, Long raiaId) {
        if (raiaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Raia é obrigatória");
        }
        var raia = raiaRepository.findByIdRaiaAndContrato_IdContrato(raiaId, contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Raia não encontrada"));
        exigirServicoAtivo(raia.getContrato(), ContratoServicos.daRaia(raia));
        return raia;
    }

    private ContratoKanbanTask buscarTask(Long contratoId, Long taskId) {
        var task = taskRepository.findByIdTaskAndContrato_IdContrato(taskId, contratoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada"));
        exigirServicoAtivo(task.getContrato(), ContratoServicos.daTarefa(task));
        return task;
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

    private ServicoComercial resolverServico(Contrato contrato, ServicoComercial servico) {
        var servicos = ContratoServicos.listar(contrato);
        if (servico != null && !servicos.contains(servico)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Este serviço não pertence ao contrato.");
        if (servico == null && !servicos.isEmpty()) {
            servico = servicos.stream().filter(item -> !ContratoServicos.encerrado(contrato, item)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Os serviços deste contrato foram encerrados."));
        }
        exigirServicoAtivo(contrato, servico);
        return servico;
    }

    private void exigirServicoAtivo(Contrato contrato, ServicoComercial servico) {
        if (ContratoServicos.encerrado(contrato, servico)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Este serviço do contrato foi encerrado.");
    }

    private void exigirMesmoServico(ContratoKanbanTask task, ContratoKanbanRaia destino) {
        if (!Objects.equals(ContratoServicos.daTarefa(task), ContratoServicos.daRaia(destino))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A tarefa deve permanecer no Kanban do seu serviço.");
        }
    }

    private int proximaPosicaoRaia(Long contratoId, ServicoComercial servico) {
        return raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(contratoId)
                .stream()
                .filter(raia -> Objects.equals(ContratoServicos.daRaia(raia), servico))
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
