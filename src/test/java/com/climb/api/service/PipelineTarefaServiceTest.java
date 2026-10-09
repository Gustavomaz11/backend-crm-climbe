package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineTarefaRequestDTO;
import com.climb.api.model.enums.*;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineTarefaServiceTest {
    @Mock private PipelineVendasTarefaRepository repository;
    @Mock private PipelineVendasNegocioRepository negocioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PipelineHistoricoService historicoService;
    @Mock private PipelineCadenciaEngine cadenciaEngine;
    @Mock private RbacService rbacService;
    @Mock private CargoHierarquiaAcessoService cargoHierarquiaAcessoService;

    private PipelineTarefaService service;

    @BeforeEach
    void setUp() {
        service = new PipelineTarefaService(
                repository, negocioRepository, usuarioRepository, historicoService, cadenciaEngine, rbacService,
                cargoHierarquiaAcessoService
        );
    }

    @Test
    void deveListarSomenteTarefasAtrasadasEAbertas() {
        Usuario responsavel = usuario(2L, "Maria");
        PipelineVendasNegocio negocio = negocio(10L, responsavel);
        PipelineVendasTarefa atrasada = tarefa(1L, negocio, responsavel, LocalDate.now().minusDays(1), PipelineTarefaStatus.PENDENTE);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS)).thenReturn(true);
        when(repository.findFiltradas(
                eq(false), eq(false), eq(true), eq(false), eq(false), any(LocalDate.class),
                eq(true), eq(Set.of(1L)), isNull(), isNull(), isNull()))
                .thenReturn(List.of(atrasada));

        var resultado = service.listar(1L, PipelineTarefaVisao.ATRASADAS, null, null, null, null);

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.getFirst().id());
        verify(repository).findFiltradas(
                eq(false), eq(false), eq(true), eq(false), eq(false), any(LocalDate.class),
                eq(true), eq(Set.of(1L)), isNull(), isNull(), isNull());
    }

    @Test
    void deveForcarFiltroPeloUsuarioQuandoNaoPodeVisualizarTodasAsTarefas() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS)).thenReturn(false);
        when(cargoHierarquiaAcessoService.buscarUsuariosVisiveis(1L)).thenReturn(Set.of(1L, 2L));

        service.listar(1L, PipelineTarefaVisao.TODAS, 99L, null, null, null);

        verifyNoInteractions(repository);
    }

    @Test
    void deveAplicarEscopoHierarquicoQuandoNaoPodeVisualizarTodasAsTarefas() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS)).thenReturn(false);
        when(cargoHierarquiaAcessoService.buscarUsuariosVisiveis(1L)).thenReturn(Set.of(1L, 2L));
        when(repository.findFiltradas(
                eq(true), eq(false), eq(false), eq(false), eq(false), any(LocalDate.class),
                eq(false), eq(Set.of(1L, 2L)), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        service.listar(1L, PipelineTarefaVisao.TODAS, null, null, null, null);

        verify(repository).findFiltradas(
                eq(true), eq(false), eq(false), eq(false), eq(false), any(LocalDate.class),
                eq(false), eq(Set.of(1L, 2L)), isNull(), isNull(), isNull());
    }

    @Test
    void deveListarNoNegocioSomenteTarefasDoUsuarioSemPermissaoGlobal() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_KANBAN_VISUALIZAR_TODAS_TAREFAS)).thenReturn(false);
        when(negocioRepository.existsById(10L)).thenReturn(true);
        when(cargoHierarquiaAcessoService.buscarUsuariosVisiveis(1L)).thenReturn(Set.of(1L, 2L));
        when(repository.findFiltradas(
                eq(true), eq(false), eq(false), eq(false), eq(false), any(LocalDate.class),
                eq(false), eq(Set.of(1L, 2L)), eq(10L), isNull(), isNull()))
                .thenReturn(List.of());

        service.listarDoNegocio(10L, 1L);

        verify(repository).findFiltradas(
                eq(true), eq(false), eq(false), eq(false), eq(false), any(LocalDate.class),
                eq(false), eq(Set.of(1L, 2L)), eq(10L), isNull(), isNull());
        verify(repository, never()).findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(10L);
    }

    @Test
    void deveRegistrarCriacaoDaTarefaNoHistorico() {
        Usuario autor = usuario(1L, "Gestor");
        Usuario responsavel = usuario(2L, "Maria");
        PipelineVendasNegocio negocio = negocio(10L, responsavel);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CRIAR)).thenReturn(true);
        when(negocioRepository.findById(10L)).thenReturn(Optional.of(negocio));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(autor));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(responsavel));
        when(repository.save(any())).thenAnswer(invocation -> {
            PipelineVendasTarefa tarefa = invocation.getArgument(0);
            tarefa.setIdTarefa(20L);
            return tarefa;
        });

        var resultado = service.criar(10L, 1L, request(PipelineTarefaStatus.PENDENTE));

        assertEquals(20L, resultado.id());
        verify(historicoService).registrar(
                eq(negocio), eq(1L), eq(PipelineHistoricoTipo.CRIACAO_TAREFA), contains("Enviar proposta")
        );
    }

    @Test
    void deveImpedirCriarTarefaConcluidaSemPermissaoDeConclusao() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CRIAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar(10L, 1L, request(PipelineTarefaStatus.CONCLUIDA))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void deveValidarPrazoContraDataDeInicio() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CRIAR)).thenReturn(true);
        PipelineTarefaRequestDTO invalido = new PipelineTarefaRequestDTO(
                "Enviar proposta", null, 2L, LocalDate.now().plusDays(2), LocalDate.now().plusDays(1),
                PipelineTarefaPrioridade.ALTA, PipelineTarefaStatus.PENDENTE, "Proposta", null, List.of()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar(10L, 1L, invalido)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void deveExigirPrazoMesmoSemDataDeInicio() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CRIAR)).thenReturn(true);
        PipelineTarefaRequestDTO invalido = new PipelineTarefaRequestDTO(
                "Enviar proposta", null, 2L, null, null,
                PipelineTarefaPrioridade.ALTA, PipelineTarefaStatus.PENDENTE, "Proposta", null, List.of()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar(10L, 1L, invalido)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("O prazo é obrigatório", exception.getReason());
        verifyNoInteractions(negocioRepository, usuarioRepository, repository);
    }

    @Test
    void cancelamentoExigeMotivoEPreservaAuditoriaSemConclusao() {
        var usuario = usuario(1L, "Operador");
        var tarefa = tarefa(2L, negocio(10L, usuario), usuario, LocalDate.now(), PipelineTarefaStatus.PENDENTE);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.cancelar(2L, 1L, "", null));
        when(repository.findById(2L)).thenReturn(Optional.of(tarefa));
        when(repository.save(tarefa)).thenReturn(tarefa);
        assertThrows(ResponseStatusException.class, () -> service.alterarStatus(2L, 1L, PipelineTarefaStatus.CANCELADA));
        var dto = service.cancelar(2L, 1L, "SEM_CANAL_CONTATO", "Telefone não disponível");
        assertEquals(PipelineTarefaStatus.CANCELADA, dto.status());
        assertEquals("SEM_CANAL_CONTATO", dto.motivoCancelamento());
        assertNotNull(dto.canceladoEm()); assertNull(dto.concluidoEm());
        service.cancelar(2L, 1L, "SEM_CANAL_CONTATO", "Repetição da requisição");
        verify(cadenciaEngine, times(1)).tarefaConcluida(tarefa);
        verify(historicoService).registrar(eq(tarefa.getNegocio()), eq(1L), eq(PipelineHistoricoTipo.CANCELAMENTO_TAREFA), anyString());
    }

    @Test
    void tarefaAtrasadaSomenteConcluiComJustificativaEPreservaHistorico() {
        var usuario = usuario(1L, "Operador");
        var task = tarefa(20L, negocio(10L, usuario), usuario, TarefaPrazoPolicy.hoje().minusDays(1), PipelineTarefaStatus.PENDENTE);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR)).thenReturn(true);
        when(repository.findById(20L)).thenReturn(Optional.of(task));
        assertThrows(ResponseStatusException.class, () -> service.alterarStatus(20L, 1L, PipelineTarefaStatus.CONCLUIDA, "  "));
        assertEquals(PipelineTarefaStatus.PENDENTE, task.getStatus());
        when(repository.save(task)).thenReturn(task);
        var resposta = service.alterarStatus(20L, 1L, PipelineTarefaStatus.CONCLUIDA, "  Aguardando retorno do cliente.  ");
        assertEquals("Aguardando retorno do cliente.", resposta.justificativaAtraso());
        verify(historicoService).registrar(eq(task.getNegocio()), eq(1L), eq(PipelineHistoricoTipo.CONCLUSAO_TAREFA), contains("Justificativa do atraso"));
    }

    @Test
    void concluirNoDiaDoPrazoNaoExigeJustificativa() {
        var usuario = usuario(1L, "Operador");
        var task = tarefa(20L, negocio(10L, usuario), usuario, TarefaPrazoPolicy.hoje(), PipelineTarefaStatus.PENDENTE);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR)).thenReturn(true);
        when(repository.findById(20L)).thenReturn(Optional.of(task)); when(repository.save(task)).thenReturn(task);
        assertEquals(PipelineTarefaStatus.CONCLUIDA, service.alterarStatus(20L, 1L, PipelineTarefaStatus.CONCLUIDA).status());
    }

    @Test
    void atualizarCamposTambemExigeJustificativaSemContornarAlterandoPrazo() {
        var usuario = usuario(1L, "Operador");
        var task = tarefa(20L, negocio(10L, usuario), usuario, TarefaPrazoPolicy.hoje().minusDays(1), PipelineTarefaStatus.PENDENTE);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_EDITAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CONCLUIR)).thenReturn(true);
        when(repository.findById(20L)).thenReturn(Optional.of(task));
        assertThrows(ResponseStatusException.class, () -> service.atualizar(20L, 1L, request(PipelineTarefaStatus.CONCLUIDA)));
        assertEquals(PipelineTarefaStatus.PENDENTE, task.getStatus()); verify(repository, never()).save(any());
    }

    private PipelineTarefaRequestDTO request(PipelineTarefaStatus status) {
        return new PipelineTarefaRequestDTO(
                "Enviar proposta", "Revisar valores", 2L, LocalDate.now(), LocalDate.now().plusDays(1),
                PipelineTarefaPrioridade.ALTA, status, "Proposta", "Confirmar recebimento", List.of()
        );
    }

    @Test
    void deveSalvarMultiplosResponsaveisEAtribuicaoDaSubtarefa() {
        Usuario ana = usuario(2L, "Ana");
        Usuario bia = usuario(3L, "Bia");
        PipelineVendasNegocio negocio = negocio(10L, ana);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_CRIAR)).thenReturn(true);
        when(negocioRepository.findById(10L)).thenReturn(Optional.of(negocio));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "Gestor")));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(bia));
        when(repository.save(any())).thenAnswer(invocation -> {
            PipelineVendasTarefa tarefa = invocation.getArgument(0); tarefa.setIdTarefa(20L); return tarefa;
        });
        var request = new PipelineTarefaRequestDTO("Conferir", null, null, null, LocalDate.now(), PipelineTarefaPrioridade.MEDIA,
                PipelineTarefaStatus.PENDENTE, "Follow-up", null,
                List.of(new com.climb.api.model.dto.PipelineSubtarefaRequestDTO("Conferir balanço", false, 0, 3L)), List.of(2L, 3L));
        var resposta = service.criar(10L, 1L, request);
        assertEquals(2, resposta.responsaveis().size());
        assertEquals(3L, resposta.subtarefas().getFirst().responsavel().id());
    }

    @Test
    void segundoResponsavelPodeAcessarColaboracao() {
        Usuario ana = usuario(2L, "Ana");
        Usuario bia = usuario(3L, "Bia");
        PipelineVendasTarefa task = tarefa(20L, negocio(10L, ana), ana, LocalDate.now(), PipelineTarefaStatus.PENDENTE);
        task.setResponsaveis(List.of(ana, bia));
        when(repository.findById(20L)).thenReturn(Optional.of(task));
        when(rbacService.temPermissao(3L, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR)).thenReturn(true);
        when(cargoHierarquiaAcessoService.buscarUsuariosVisiveis(3L)).thenReturn(Set.of(3L));
        assertEquals(task, service.exigirTarefaVisivel(20L, 3L));
    }

    private Usuario usuario(Long id, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
        usuario.setSituacao("ATIVO");
        return usuario;
    }

    private PipelineVendasNegocio negocio(Long id, Usuario responsavel) {
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(id);
        PipelineVendasFunil funil = new PipelineVendasFunil();
        funil.setIdFunil(1L);
        funil.setNome("Funil padrão");
        negocio.setFunil(funil);
        negocio.setNomeEmpresa("Apex Ventures");
        negocio.setResponsavel(responsavel);
        return negocio;
    }

    private PipelineVendasTarefa tarefa(Long id,
                                        PipelineVendasNegocio negocio,
                                        Usuario responsavel,
                                        LocalDate prazo,
                                        PipelineTarefaStatus status) {
        PipelineVendasTarefa tarefa = new PipelineVendasTarefa();
        tarefa.setIdTarefa(id);
        tarefa.setNegocio(negocio);
        tarefa.setTitulo("Tarefa " + id);
        tarefa.setResponsavel(responsavel);
        tarefa.setPrazo(prazo);
        tarefa.setPrioridade(PipelineTarefaPrioridade.MEDIA);
        tarefa.setStatus(status);
        tarefa.setTipo("Follow-up");
        return tarefa;
    }
}
