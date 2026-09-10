package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.PipelineCampanhaExecucaoRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineCadenciaEngineTest {
    @Mock private PipelineCampanhaExecucaoRepository execucaoRepository;
    @Mock private PipelineVendasTarefaRepository tarefaRepository;
    @Mock private PipelineHistoricoService historicoService;
    private PipelineCadenciaEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PipelineCadenciaEngine(execucaoRepository, tarefaRepository,
                new PipelineDiasUteis(), new PipelineTemplateRenderer(), historicoService);
        when(tarefaRepository.save(any())).thenAnswer(invocation -> {
            PipelineVendasTarefa tarefa = invocation.getArgument(0);
            if (tarefa.getIdTarefa() == null) tarefa.setIdTarefa(System.nanoTime());
            return tarefa;
        });
    }

    @Test
    void deveCriarProximaTarefaSomenteAposConclusaoDaAnterior() {
        PipelineCampanhaExecucao execucao = execucaoComDuasTarefas();
        when(execucaoRepository.buscarAtivasParaProcessamento()).thenReturn(List.of(execucao));

        engine.processarPendentes();
        engine.processarPendentes();

        ArgumentCaptor<PipelineVendasTarefa> tarefas = ArgumentCaptor.forClass(PipelineVendasTarefa.class);
        verify(tarefaRepository, times(1)).save(tarefas.capture());
        PipelineVendasTarefa primeira = tarefas.getValue();
        assertEquals("Enviar mensagem para Maria", primeira.getTitulo());
        assertTrue(primeira.getDescricao().contains("Apex Ventures"));

        primeira.setStatus(PipelineTarefaStatus.CONCLUIDA);
        when(execucaoRepository.findByTarefaAtualIdTarefa(primeira.getIdTarefa())).thenReturn(Optional.of(execucao));
        engine.tarefaConcluida(primeira);

        verify(tarefaRepository, times(2)).save(any(PipelineVendasTarefa.class));
        assertEquals(1, execucao.getOrdemAtual());
        assertEquals("Criar ligação", execucao.getTarefaAtual().getTitulo());
    }

    @Test
    void cancelamentoLiberaProximaTarefaSemContarComoConclusao() {
        var execucao = execucaoComDuasTarefas();
        when(execucaoRepository.buscarAtivasParaProcessamento()).thenReturn(List.of(execucao));
        engine.processarPendentes();
        var primeira = execucao.getTarefaAtual();
        primeira.setStatus(PipelineTarefaStatus.CANCELADA);
        when(execucaoRepository.findByTarefaAtualIdTarefa(primeira.getIdTarefa())).thenReturn(Optional.of(execucao));
        engine.tarefaConcluida(primeira);
        engine.processarPendentes();
        verify(tarefaRepository, times(2)).save(any());
        assertEquals(PipelineTarefaStatus.CANCELADA, primeira.getStatus());
        assertEquals("Criar ligação", execucao.getTarefaAtual().getTitulo());
    }

    @Test
    void execucaoMantemVersaoEScriptMesmoAposEdicaoDaCampanha() {
        var execucao = execucaoComDuasTarefas();
        var antiga = execucao.getCampanha().getEtapas().getFirst();
        antiga.setScriptModelo("Mensagem original para {{nome_pessoa}}");
        PipelineScript script = new PipelineScript(); script.setModeloMensagem("Texto modificado"); antiga.setScript(script);
        execucao.getCampanha().setVersao(2);
        var nova = tarefa(0, "Nova sequência", null, "Contato"); nova.setVersao(2);
        execucao.getCampanha().getEtapas().add(nova);
        when(execucaoRepository.buscarAtivasParaProcessamento()).thenReturn(List.of(execucao));
        engine.processarPendentes();
        assertEquals("Enviar mensagem para Maria", execucao.getTarefaAtual().getTitulo());
        assertTrue(execucao.getTarefaAtual().getDescricao().contains("Mensagem original para Maria"));
    }

    @Test
    void sairDaEtapaCancelaPendenciaEInterrompeFluxo() {
        var execucao = execucaoComDuasTarefas();
        PipelineVendasEtapa etapa = new PipelineVendasEtapa(); etapa.setCodigo("TENTATIVA_CONTATO");
        execucao.getNegocio().setEtapa(etapa); execucao.setEtapaFunilCodigo("TENTATIVA_CONTATO");
        execucao.getCampanha().getEtapas().forEach(e -> e.setEtapaFunilCodigo("TENTATIVA_CONTATO"));
        when(execucaoRepository.buscarAtivasParaProcessamento()).thenReturn(List.of(execucao));
        engine.processarPendentes();
        var tarefa = execucao.getTarefaAtual();
        etapa.setCodigo("LEAD_CONECTADO");
        when(execucaoRepository.findByNegocioIdNegocio(10L)).thenReturn(List.of(execucao));
        engine.encerrarForaDaEtapa(execucao.getNegocio());
        assertEquals(PipelineExecucaoStatus.INTERROMPIDA, execucao.getStatus());
        assertEquals(PipelineTarefaStatus.CANCELADA, tarefa.getStatus());
        assertEquals("MOVIMENTACAO_ETAPA", tarefa.getMotivoCancelamento());
    }

    private PipelineCampanhaExecucao execucaoComDuasTarefas() {
        Usuario responsavel = new Usuario();
        responsavel.setId(1L);
        responsavel.setNomeCompleto("João");
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(10L);
        negocio.setNomeContato("Maria");
        negocio.setNomeEmpresa("Apex Ventures");
        negocio.setServicoInteresse("M&A");
        negocio.setResponsavel(responsavel);
        negocio.setResultado(PipelineVendasResultado.ABERTO);

        PipelineCampanha campanha = new PipelineCampanha();
        campanha.setIdCampanha(20L);
        campanha.setNome("Prospecção consultiva");
        campanha.setAtivo(true);
        campanha.setCriadoPor(responsavel);
        campanha.setDiasExecucao(new LinkedHashSet<>(Set.of(1, 2, 3, 4, 5, 6, 7)));
        campanha.substituirEtapas(List.of(
                tarefa(0, "Enviar mensagem para {{nome_pessoa}}", "Falar com {{nome_empresa}}", "Mensagem"),
                tarefa(1, "Criar ligação", null, "Ligação"),
                terminal(2)
        ));

        PipelineCampanhaExecucao execucao = new PipelineCampanhaExecucao();
        execucao.setCampanha(campanha);
        execucao.setNegocio(negocio);
        execucao.setParticipante(responsavel);
        execucao.setStatus(PipelineExecucaoStatus.ATIVA);
        execucao.setOrdemAtual(0);
        execucao.setProximaExecucaoEm(LocalDateTime.now().minusMinutes(1));
        return execucao;
    }

    private PipelineCadenciaEtapa tarefa(int ordem, String titulo, String descricao, String tipo) {
        PipelineCadenciaEtapa etapa = new PipelineCadenciaEtapa();
        etapa.setOrdem(ordem);
        etapa.setTipo(PipelineCadenciaTipo.TAREFA);
        etapa.setTitulo(titulo);
        etapa.setDescricao(descricao);
        etapa.setTipoTarefa(tipo);
        etapa.setPrioridade(PipelineTarefaPrioridade.MEDIA);
        etapa.setPrazoDiasUteis(0);
        return etapa;
    }

    private PipelineCadenciaEtapa terminal(int ordem) {
        PipelineCadenciaEtapa etapa = new PipelineCadenciaEtapa();
        etapa.setOrdem(ordem);
        etapa.setTipo(PipelineCadenciaTipo.ENCERRAR);
        return etapa;
    }
}
