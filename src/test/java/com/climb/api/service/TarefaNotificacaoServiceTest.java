package com.climb.api.service;

import com.climb.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TarefaNotificacaoServiceTest {
    private final NotificacaoEntregaService entrega = mock(NotificacaoEntregaService.class);
    private final TarefaNotificacaoService service = new TarefaNotificacaoService(entrega);
    private ContratoKanbanTask tarefa;
    private Usuario lider, ana, bia;
    @BeforeEach void preparar() {
        lider = usuario(1L); ana = usuario(2L); bia = usuario(3L);
        Contrato contrato = new Contrato(); contrato.setIdContrato(10L); contrato.setResponsavel(lider); contrato.setEmpresaNomeFantasia("Cliente");
        tarefa = new ContratoKanbanTask(); tarefa.setIdTask(20L); tarefa.setContrato(contrato); tarefa.setTitulo("Conferir documento");
        tarefa.setResponsaveis(List.of(ana, bia)); tarefa.setRaia(raia("À fazer", false));
    }
    @Test void criacaoNotificaLiderEAbreTarefaCorreta() {
        service.contratoCriada(tarefa);
        verify(entrega).notificar(eq(lider), eq("TAREFA_CRIADA"), anyString(), contains("Conferir documento"), eq("/contratos/kanban?contrato=10&tarefaId=20"));
        verifyNoMoreInteractions(entrega);
    }
    @Test void atribuicaoAvisaSomenteNovosResponsaveis() {
        service.atribuicoes(tarefa, Set.of(ana.getId()));
        verify(entrega).notificar(eq(bia), eq("TAREFA_ATRIBUIDA"), anyString(), anyString(), eq("/contratos/kanban?contrato=10&tarefaId=20"));
        verifyNoMoreInteractions(entrega);
    }
    @Test void voltarParaAFazerEConcluirNotificaLiderUmaVezPorTransicao() {
        service.contratoMovida(tarefa, "EM_ANDAMENTO");
        verify(entrega).notificar(eq(lider), eq("TAREFA_A_FAZER"), anyString(), anyString(), anyString());
        tarefa.setRaia(raia("Entregue", true));
        service.contratoMovida(tarefa, "A_FAZER");
        verify(entrega).notificar(eq(lider), eq("TAREFA_CONCLUIDA"), anyString(), anyString(), anyString());
        service.contratoMovida(tarefa, "CONCLUIDA");
        service.atribuicoes(tarefa, Set.of(2L, 3L));
        verifyNoMoreInteractions(entrega);
    }
    @Test void novaAtribuicaoEmPreVendasDirecionaAoMenuPreVendas() {
        PipelineVendasFunil funil = new PipelineVendasFunil(); funil.setIdFunil(4L); funil.setTipo("PRE_VENDAS");
        PipelineVendasNegocio negocio = new PipelineVendasNegocio(); negocio.setIdNegocio(5L); negocio.setFunil(funil); negocio.setNomeEmpresa("Cliente");
        PipelineVendasTarefa atividade = new PipelineVendasTarefa(); atividade.setIdTarefa(6L); atividade.setNegocio(negocio); atividade.setTitulo("Contato"); atividade.setResponsaveis(List.of(ana));
        service.atribuicoes(atividade, Set.of());
        verify(entrega).notificar(eq(ana), eq("TAREFA_ATRIBUIDA"), anyString(), anyString(), eq("/pipeline-pre-vendas?funilId=4&negocioId=5&tarefaId=6"));
        clearInvocations(entrega); funil.setTipo("VENDAS"); service.atribuicoes(atividade, Set.of());
        verify(entrega).notificar(eq(ana), eq("TAREFA_ATRIBUIDA"), anyString(), anyString(), eq("/pipeline-vendas?funilId=4&negocioId=5&tarefaId=6"));
    }
    private Usuario usuario(Long id) { Usuario u = new Usuario(); u.setId(id); u.setNomeCompleto("Pessoa " + id); return u; }
    private ContratoKanbanRaia raia(String titulo, boolean concluida) { ContratoKanbanRaia r = new ContratoKanbanRaia(); r.setTitulo(titulo); r.setConcluiTarefas(concluida); return r; }
}
