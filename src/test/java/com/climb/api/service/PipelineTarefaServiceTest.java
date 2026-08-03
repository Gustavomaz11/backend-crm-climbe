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

    private PipelineTarefaService service;

    @BeforeEach
    void setUp() {
        service = new PipelineTarefaService(
                repository, negocioRepository, usuarioRepository, historicoService, cadenciaEngine, rbacService
        );
    }

    @Test
    void deveListarSomenteTarefasAtrasadasEAbertas() {
        Usuario responsavel = usuario(2L, "Maria");
        PipelineVendasNegocio negocio = negocio(10L, responsavel);
        PipelineVendasTarefa atrasada = tarefa(1L, negocio, responsavel, LocalDate.now().minusDays(1), PipelineTarefaStatus.PENDENTE);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_TAREFA_VISUALIZAR)).thenReturn(true);
        when(repository.findFiltradas(
                eq(false), eq(false), eq(true), eq(false), eq(false), any(LocalDate.class),
                isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(atrasada));

        var resultado = service.listar(1L, PipelineTarefaVisao.ATRASADAS, null, null, null, null);

        assertEquals(1, resultado.size());
        assertEquals(1L, resultado.getFirst().id());
        verify(repository).findFiltradas(
                eq(false), eq(false), eq(true), eq(false), eq(false), any(LocalDate.class),
                isNull(), isNull(), isNull(), isNull());
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

    private PipelineTarefaRequestDTO request(PipelineTarefaStatus status) {
        return new PipelineTarefaRequestDTO(
                "Enviar proposta", "Revisar valores", 2L, LocalDate.now(), LocalDate.now().plusDays(1),
                PipelineTarefaPrioridade.ALTA, status, "Proposta", "Confirmar recebimento", List.of()
        );
    }

    private Usuario usuario(Long id, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
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
