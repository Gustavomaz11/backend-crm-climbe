package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoKanbanRaia;
import com.climb.api.model.ContratoKanbanSubtarefa;
import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ContratoKanbanBoardResponseDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaConclusaoRequestDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaRequestDTO;
import com.climb.api.model.dto.ContratoKanbanTaskRequestDTO;
import com.climb.api.model.enums.ContratoKanbanPrioridade;
import com.climb.api.repository.ContratoKanbanRaiaRepository;
import com.climb.api.repository.ContratoKanbanSubtarefaRepository;
import com.climb.api.repository.ContratoKanbanTaskRepository;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratoKanbanServiceTest {

    private static final Long CONTRATO_ID = 10L;
    private static final Long GESTOR_ID = 1L;

    @Mock
    private ContratoRepository contratoRepository;
    @Mock
    private ContratoKanbanRaiaRepository raiaRepository;
    @Mock
    private ContratoKanbanTaskRepository taskRepository;
    @Mock
    private ContratoKanbanSubtarefaRepository subtarefaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RbacService rbacService;
    @Mock
    private CargoHierarquiaAcessoService cargoHierarquiaAcessoService;

    @Mock private ContratoEquipeService equipeService;
    @Mock private ContratoRateioTecnicoService rateioService;
    @Mock private ContratoApoioAtuacaoService atuacaoService;

    @InjectMocks
    private ContratoKanbanService service;

    private Contrato contrato;
    private ContratoKanbanRaia raia;
    private Usuario gestor;

    @BeforeEach
    void setUp() {
        gestor = usuario(1L, "Gestor", "ATIVO");
        contrato = new Contrato();
        contrato.setIdContrato(CONTRATO_ID);
        contrato.setResponsavel(gestor);
        contrato.setUrlPdf("contrato.pdf");
        contrato.setStatus("APROVADO");
        contrato.setEquipeConfigurada(true);
        org.mockito.Mockito.lenient().when(equipeService.podeEditar(any(), any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(equipeService.membrosAtuais(any())).thenReturn(List.of(gestor));

        raia = new ContratoKanbanRaia();
        raia.setIdRaia(20L);
        raia.setContrato(contrato);
        raia.setTitulo("A fazer");
        raia.setPosicao(0);
    }

    @Test
    void deveCriarTarefaComPrioridadeEQualquerUsuarioAtivo() {
        Usuario responsavel = usuario(2L, "Analista", "ATIVO");
        prepararGestorEBoardVazio(List.of(responsavel));
        when(raiaRepository.findByIdRaiaAndContrato_IdContrato(20L, CONTRATO_ID)).thenReturn(Optional.of(raia));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(responsavel));

        ContratoKanbanTaskRequestDTO request = new ContratoKanbanTaskRequestDTO(
                20L,
                "Analisar documentos",
                "Conferir os anexos",
                ContratoKanbanPrioridade.ALTA,
                2L,
                null,
                null,
                null
        );

        service.criarTask(CONTRATO_ID, GESTOR_ID, request);

        ArgumentCaptor<ContratoKanbanTask> captor = ArgumentCaptor.forClass(ContratoKanbanTask.class);
        verify(taskRepository).save(captor.capture());
        ContratoKanbanTask tarefa = captor.getValue();
        assertEquals(ContratoKanbanPrioridade.ALTA, tarefa.getPrioridade());
        assertEquals(responsavel, tarefa.getResponsavel());
        assertEquals("Conferir os anexos", tarefa.getDescricao());
    }

    @Test
    void deveRecusarResponsavelInativo() {
        Usuario inativo = usuario(2L, "Usuário inativo", "INATIVO");
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(raiaRepository.findByIdRaiaAndContrato_IdContrato(20L, CONTRATO_ID)).thenReturn(Optional.of(raia));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(inativo));

        ContratoKanbanTaskRequestDTO request = new ContratoKanbanTaskRequestDTO(
                20L,
                "Tarefa",
                null,
                ContratoKanbanPrioridade.MEDIA,
                2L,
                null,
                null,
                0
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criarTask(CONTRATO_ID, GESTOR_ID, request)
        );

        assertTrue(exception.getReason().contains("usuário ativo"));
        verify(taskRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {2L})
    void deveManterTarefaCriadaVisivelParaGestorSemPermissaoGlobal(Long responsavelId) {
        Usuario analista = usuario(2L, "Analista", "ATIVO");
        List<ContratoKanbanTask> tarefasSalvas = new ArrayList<>();
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(raiaRepository.findByIdRaiaAndContrato_IdContrato(20L, CONTRATO_ID)).thenReturn(Optional.of(raia));
        prepararConsultasDoBoard(tarefasSalvas, List.of(), List.of(gestor, analista));
        if (responsavelId != null) {
            when(usuarioRepository.findById(responsavelId)).thenReturn(Optional.of(analista));
        }
        when(taskRepository.save(any())).thenAnswer(invocation -> {
            ContratoKanbanTask task = invocation.getArgument(0);
            task.setIdTask(30L);
            tarefasSalvas.add(task);
            return task;
        });

        ContratoKanbanTaskRequestDTO request = new ContratoKanbanTaskRequestDTO(
                20L, "Analisar documentos", null, ContratoKanbanPrioridade.MEDIA,
                responsavelId, null, null, 0);

        ContratoKanbanBoardResponseDTO criado = service.criarTask(CONTRATO_ID, GESTOR_ID, request);

        assertTrue(criado.gestor());
        assertEquals(1, criado.raias().getFirst().tasks().size());
        assertEquals("Analisar documentos", criado.raias().getFirst().tasks().getFirst().titulo());

        ContratoKanbanBoardResponseDTO recarregado = service.buscarBoard(CONTRATO_ID, GESTOR_ID);

        assertEquals(1, recarregado.raias().getFirst().tasks().size());
        assertEquals(30L, recarregado.raias().getFirst().tasks().getFirst().id());
        verify(cargoHierarquiaAcessoService, never()).buscarUsuariosVisiveis(GESTOR_ID);
    }

    @Test
    void deveCriarSubtarefaNaProximaPosicao() {
        ContratoKanbanTask tarefa = tarefa(30L, gestor);
        ContratoKanbanSubtarefa existente = subtarefa(40L, tarefa, "Primeira", false, 0);
        prepararGestorEBoardVazio(List.of(gestor));
        when(taskRepository.findByIdTaskAndContrato_IdContrato(30L, CONTRATO_ID)).thenReturn(Optional.of(tarefa));
        when(subtarefaRepository.findByTask_IdTaskOrderByPosicaoAscIdSubtarefaAsc(30L))
                .thenReturn(List.of(existente));

        service.criarSubtarefa(
                CONTRATO_ID,
                30L,
                GESTOR_ID,
                new ContratoKanbanSubtarefaRequestDTO("Segunda", false, null)
        );

        ArgumentCaptor<ContratoKanbanSubtarefa> captor = ArgumentCaptor.forClass(ContratoKanbanSubtarefa.class);
        verify(subtarefaRepository).save(captor.capture());
        assertEquals("Segunda", captor.getValue().getTitulo());
        assertEquals(1, captor.getValue().getPosicao());
    }

    @Test
    void responsavelDaTarefaDevePoderConcluirSubtarefa() {
        Usuario responsavel = usuario(2L, "Analista", "ATIVO");
        ContratoKanbanTask tarefa = tarefa(30L, responsavel);
        ContratoKanbanSubtarefa subtarefa = subtarefa(40L, tarefa, "Checklist", false, 0);
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(taskRepository.findByIdTaskAndContrato_IdContrato(30L, CONTRATO_ID)).thenReturn(Optional.of(tarefa));
        when(subtarefaRepository.findByIdSubtarefaAndTask_IdTaskAndTask_Contrato_IdContrato(40L, 30L, CONTRATO_ID))
                .thenReturn(Optional.of(subtarefa));
        prepararConsultasDoBoard(List.of(tarefa), List.of(subtarefa), List.of(responsavel));

        service.atualizarConclusaoSubtarefa(
                CONTRATO_ID,
                30L,
                40L,
                2L,
                new ContratoKanbanSubtarefaConclusaoRequestDTO(true)
        );

        assertTrue(subtarefa.isConcluida());
        verify(subtarefaRepository).save(subtarefa);
    }

    @Test
    void boardDeveExporUsuariosAtivosDisponiveisParaAtribuicao() {
        Usuario analista = usuario(2L, "Analista", "ATIVO");
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        prepararConsultasDoBoard(List.of(), List.of(), List.of(gestor, analista));

        ContratoKanbanBoardResponseDTO board = service.buscarBoard(CONTRATO_ID, GESTOR_ID);

        assertEquals(2, board.usuariosDisponiveis().size());
        assertEquals("Analista", board.usuariosDisponiveis().get(1).nomeCompleto());
    }

    @Test
    void membroDaEquipeDeveVerTodasAsTarefasSemPermissaoGlobal() {
        Usuario analista = usuario(2L, "Analista", "ATIVO");
        ContratoKanbanTask tarefaDoGestor = tarefa(30L, gestor);
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(equipeService.membrosAtuais(contrato)).thenReturn(List.of(gestor, analista));
        prepararConsultasDoBoard(List.of(tarefaDoGestor), List.of(), List.of(gestor, analista));
        ContratoKanbanBoardResponseDTO board = service.buscarBoard(CONTRATO_ID, 2L);
        assertEquals(1, board.raias().getFirst().tasks().size());
        assertTrue(board.podeEditar());
        verify(equipeService).exigirAcesso(contrato, 2L, false);
        verify(taskRepository).findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(CONTRATO_ID);
    }

    @Test
    void deveCriarTarefaComDoisResponsaveis() {
        Usuario ana = usuario(2L, "Ana", "ATIVO");
        Usuario bia = usuario(3L, "Bia", "ATIVO");
        prepararGestorEBoardVazio(List.of(ana, bia));
        when(raiaRepository.findByIdRaiaAndContrato_IdContrato(20L, CONTRATO_ID)).thenReturn(Optional.of(raia));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(bia));
        service.criarTask(CONTRATO_ID, GESTOR_ID, new ContratoKanbanTaskRequestDTO(20L, "Análise", null,
                ContratoKanbanPrioridade.MEDIA, null, null, null, null, List.of(2L, 3L)));
        ArgumentCaptor<ContratoKanbanTask> captor = ArgumentCaptor.forClass(ContratoKanbanTask.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(Set.of(ana, bia), captor.getValue().getResponsaveisEfetivos());
    }

    @Test
    void segundoResponsavelPodeMoverEVerTarefa() {
        Usuario ana = usuario(2L, "Ana", "ATIVO");
        Usuario bia = usuario(3L, "Bia", "ATIVO");
        ContratoKanbanTask task = tarefa(30L, ana);
        task.setResponsaveis(List.of(ana, bia));
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(taskRepository.findByIdTaskAndContrato_IdContrato(30L, CONTRATO_ID)).thenReturn(Optional.of(task));
        when(raiaRepository.findByIdRaiaAndContrato_IdContrato(20L, CONTRATO_ID)).thenReturn(Optional.of(raia));
        service.moverTask(CONTRATO_ID, 30L, 3L, new com.climb.api.model.dto.ContratoKanbanMoverTaskRequestDTO(20L));
        verify(taskRepository).save(task);
        when(taskRepository.findById(30L)).thenReturn(Optional.of(task));
        assertEquals(task, service.exigirTaskVisivel(30L, 3L));
    }

    @Test
    void deveImpedirAtribuirSubtarefaAForaDaTarefa() {
        ContratoKanbanTask task = tarefa(30L, usuario(2L, "Ana", "ATIVO"));
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        when(taskRepository.findByIdTaskAndContrato_IdContrato(30L, CONTRATO_ID)).thenReturn(Optional.of(task));
        var exception = assertThrows(ResponseStatusException.class, () -> service.criarSubtarefa(CONTRATO_ID, 30L, GESTOR_ID,
                new ContratoKanbanSubtarefaRequestDTO("Conferir", false, 0, 99L)));
        assertTrue(exception.getReason().contains("tarefa principal"));
        verify(subtarefaRepository, never()).save(any());
    }

    @Test
    void deveLimparSubtarefaAoRemoverResponsavel() {
        Usuario ana = usuario(2L, "Ana", "ATIVO");
        Usuario bia = usuario(3L, "Bia", "ATIVO");
        ContratoKanbanTask task = tarefa(30L, ana);
        task.setResponsaveis(List.of(ana, bia));
        ContratoKanbanSubtarefa sub = subtarefa(40L, task, "Conferir", false, 0);
        sub.setResponsavel(bia);
        prepararGestorEBoardVazio(List.of(ana, bia));
        when(taskRepository.findByIdTaskAndContrato_IdContrato(30L, CONTRATO_ID)).thenReturn(Optional.of(task));
        when(subtarefaRepository.findByTask_IdTaskOrderByPosicaoAscIdSubtarefaAsc(30L)).thenReturn(List.of(sub));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ana));
        service.atualizarTask(CONTRATO_ID, 30L, GESTOR_ID, new ContratoKanbanTaskRequestDTO(20L, "Conferir", null,
                ContratoKanbanPrioridade.MEDIA, null, null, null, 0, List.of(2L)));
        assertEquals(null, sub.getResponsavel());
        verify(subtarefaRepository).save(sub);
    }

    private void prepararGestorEBoardVazio(List<Usuario> usuariosAtivos) {
        when(contratoRepository.findById(CONTRATO_ID)).thenReturn(Optional.of(contrato));
        prepararConsultasDoBoard(List.of(), List.of(), usuariosAtivos);
    }

    private void prepararConsultasDoBoard(List<ContratoKanbanTask> tarefas,
                                           List<ContratoKanbanSubtarefa> subtarefas,
                                           List<Usuario> usuariosAtivos) {
        when(raiaRepository.findByContrato_IdContratoOrderByPosicaoAscIdRaiaAsc(CONTRATO_ID))
                .thenReturn(List.of(raia));
        when(taskRepository.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(CONTRATO_ID))
                .thenReturn(tarefas);
        if (!tarefas.isEmpty()) {
            Set<Long> taskIds = tarefas.stream().map(ContratoKanbanTask::getIdTask).collect(java.util.stream.Collectors.toSet());
            when(subtarefaRepository.findByTask_IdTaskInOrderByTask_IdTaskAscPosicaoAscIdSubtarefaAsc(taskIds))
                    .thenReturn(subtarefas);
        }
        org.mockito.Mockito.lenient().when(usuarioRepository.findAllBySituacaoOrderByNomeCompletoAsc("ATIVO")).thenReturn(usuariosAtivos);
    }

    private ContratoKanbanTask tarefa(Long id, Usuario responsavel) {
        ContratoKanbanTask tarefa = new ContratoKanbanTask();
        tarefa.setIdTask(id);
        tarefa.setContrato(contrato);
        tarefa.setRaia(raia);
        tarefa.setTitulo("Tarefa");
        tarefa.setPrioridade(ContratoKanbanPrioridade.MEDIA);
        tarefa.setResponsavel(responsavel);
        tarefa.setPosicao(0);
        return tarefa;
    }

    private ContratoKanbanSubtarefa subtarefa(Long id,
                                               ContratoKanbanTask tarefa,
                                               String titulo,
                                               boolean concluida,
                                               int posicao) {
        ContratoKanbanSubtarefa subtarefa = new ContratoKanbanSubtarefa();
        subtarefa.setIdSubtarefa(id);
        subtarefa.setTask(tarefa);
        subtarefa.setTitulo(titulo);
        subtarefa.setConcluida(concluida);
        subtarefa.setPosicao(posicao);
        return subtarefa;
    }

    private Usuario usuario(Long id, String nome, String situacao) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
        usuario.setEmail(nome.toLowerCase().replace(" ", ".") + "@climbe.com");
        usuario.setSituacao(situacao);
        return usuario;
    }
}
