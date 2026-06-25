package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoKanbanRaia;
import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ContratoKanbanBoardResponseDTO;
import com.climb.api.model.dto.ContratoKanbanMoverTaskRequestDTO;
import com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO;
import com.climb.api.model.dto.ContratoKanbanRaiaResponseDTO;
import com.climb.api.model.dto.ContratoKanbanTaskRequestDTO;
import com.climb.api.model.dto.ContratoKanbanTaskResponseDTO;
import com.climb.api.model.dto.UsuarioResumoDTO;
import com.climb.api.repository.ContratoKanbanRaiaRepository;
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
    private final UsuarioRepository usuarioRepository;
    private final RbacService rbacService;

    public ContratoKanbanService(ContratoRepository contratoRepository,
                                 ContratoKanbanRaiaRepository raiaRepository,
                                 ContratoKanbanTaskRepository taskRepository,
                                 UsuarioRepository usuarioRepository,
                                 RbacService rbacService) {
        this.contratoRepository = contratoRepository;
        this.raiaRepository = raiaRepository;
        this.taskRepository = taskRepository;
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
        task.setResponsavel(buscarResponsavelDaTask(contrato, dto.responsavelId()));
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
        task.setResponsavel(buscarResponsavelDaTask(contrato, dto.responsavelId()));
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

    private ContratoKanbanBoardResponseDTO toBoardResponse(Contrato contrato, Long usuarioId) {
        List<ContratoKanbanRaia> raias = raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(contrato.getIdContrato());
        List<ContratoKanbanTask> tasks = taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contrato.getIdContrato());
        Map<Long, List<ContratoKanbanTask>> tasksPorRaia = tasks.stream()
                .collect(Collectors.groupingBy(task -> task.getRaia().getIdRaia()));

        List<ContratoKanbanRaiaResponseDTO> raiasDto = raias.stream()
                .map(raia -> toRaiaResponse(raia, tasksPorRaia.getOrDefault(raia.getIdRaia(), List.of())))
                .toList();

        List<UsuarioResumoDTO> participantes = contrato.getParticipantes().stream()
                .sorted(Comparator.comparing(Usuario::getNomeCompleto, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toUsuarioResumo)
                .toList();

        return new ContratoKanbanBoardResponseDTO(
                contrato.getIdContrato(),
                contrato.getUrlPdf(),
                isGestor(contrato, usuarioId),
                toUsuarioResumo(contrato.getResponsavel()),
                participantes,
                raiasDto
        );
    }

    private ContratoKanbanRaiaResponseDTO toRaiaResponse(ContratoKanbanRaia raia, List<ContratoKanbanTask> tasks) {
        return new ContratoKanbanRaiaResponseDTO(
                raia.getIdRaia(),
                raia.getTitulo(),
                raia.getPosicao(),
                raia.getCriadoEm(),
                raia.getAtualizadoEm(),
                tasks.stream().map(this::toTaskResponse).toList()
        );
    }

    private ContratoKanbanTaskResponseDTO toTaskResponse(ContratoKanbanTask task) {
        return new ContratoKanbanTaskResponseDTO(
                task.getIdTask(),
                task.getRaia().getIdRaia(),
                task.getTitulo(),
                task.getDescricao(),
                toUsuarioResumo(task.getResponsavel()),
                task.getDataInicio(),
                task.getDataFim(),
                task.getPosicao(),
                task.getCriadoEm(),
                task.getAtualizadoEm()
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

    private Usuario buscarResponsavelDaTask(Contrato contrato, Long responsavelId) {
        if (responsavelId == null) {
            return null;
        }
        Usuario usuario = usuarioRepository.findById(responsavelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável da tarefa não encontrado"));

        boolean vinculadoAoContrato = contrato.getParticipantes().stream()
                .anyMatch(participante -> Objects.equals(participante.getId(), responsavelId))
                || (contrato.getResponsavel() != null && Objects.equals(contrato.getResponsavel().getId(), responsavelId));
        if (!vinculadoAoContrato) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável da tarefa precisa estar vinculado ao contrato");
        }
        return usuario;
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
}
