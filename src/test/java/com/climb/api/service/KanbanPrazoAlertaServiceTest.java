package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KanbanPrazoAlertaServiceTest {
    private final PipelineVendasTarefaRepository comerciais = mock(PipelineVendasTarefaRepository.class);
    private final ContratoKanbanTaskRepository contratos = mock(ContratoKanbanTaskRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final KanbanPrazoEnvioService envio = mock(KanbanPrazoEnvioService.class);
    private final KanbanPrazoAvisoService avisosInternos = mock(KanbanPrazoAvisoService.class);
    private final KanbanPrazoAlertaService service = new KanbanPrazoAlertaService(comerciais, contratos, usuarios, envio, avisosInternos, "https://app.test/");
    private final LocalDate hoje = LocalDate.of(2026, 11, 10);

    @Test void avisaTodosOsDiretoresESeparaContratoParaSeuLider() {
        when(usuarios.buscarDiretoresComerciaisAtivos()).thenReturn(List.of(usuario(1L), usuario(2L)));
        var lista = new ArrayList<PipelineVendasTarefa>();
        for (int dias : List.of(3, 1, 0, -1, -6, 2, 4)) lista.add(comercial((long) dias + 20, dias, false));
        when(comerciais.findAbertasComPrazoAte(hoje.plusDays(3))).thenReturn(lista);
        var contrato = new Contrato(); contrato.setIdContrato(30L); contrato.setResponsavel(usuario(3L));
        contrato.setEmpresaNomeFantasia("Empresa técnica"); contrato.setServico(ServicoComercial.CONTABILIDADE);
        var tarefa = new ContratoKanbanTask(); tarefa.setIdTask(40L); tarefa.setContrato(contrato);
        tarefa.setTitulo("Entrega técnica"); tarefa.setDataFim(hoje.plusDays(1)); tarefa.setResponsavel(usuario(4L));
        when(contratos.buscarAbertasComPrazoAte(hoje.plusDays(3))).thenReturn(List.of(tarefa));
        service.processar(hoje);
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(envio).enviar(eq(1L), captor.capture(), eq(hoje));
        @SuppressWarnings("unchecked") List<KanbanPrazoAtividade> avisos = captor.getValue();
        assertThat(avisos).hasSize(5).allMatch(a -> a.tipo() == TarefaTipo.COMERCIAL);
        assertThat(avisos).extracting(KanbanPrazoAtividade::marco).containsExactly(3, 1, 0, -1, -1);
        assertThat(avisos.getFirst().responsaveis()).isEqualTo("Pessoa 4, Pessoa 5");
        verify(envio).enviar(eq(2L), eq(avisos), eq(hoje));
        verify(envio).enviar(eq(3L), argThat(a -> a.size() == 1 && a.getFirst().tipo() == TarefaTipo.CONTRATO
                && a.getFirst().link().equals("https://app.test/contratos/kanban?contrato=30&tarefaId=40&servico=CONTABILIDADE")), eq(hoje));
        verify(envio, never()).enviar(eq(4L), anyList(), any());
        verify(avisosInternos).notificar(eq(3L), argThat(a -> a.size() == 1 && a.getFirst().tipo() == TarefaTipo.CONTRATO), eq(hoje));
        verify(avisosInternos).notificar(eq(4L), argThat(a -> a.size() == 6), eq(hoje));
        verify(avisosInternos).notificar(eq(5L), argThat(a -> a.size() == 5), eq(hoje));
        verifyNoMoreInteractions(avisosInternos);
    }
    @Test void vencimentoEAtrasoAvisamAtribuidosETecnicoDoServicoSemDuplicarPapeis() {
        var contrato = new Contrato(); contrato.setIdContrato(30L); contrato.setResponsavel(usuario(1L));
        contrato.getResponsaveisPorServico().put(ServicoComercial.CFO, usuario(2L));
        contrato.getResponsaveisPorServico().put(ServicoComercial.CONTABILIDADE, usuario(3L));
        var raia = new ContratoKanbanRaia(); raia.setServico(ServicoComercial.CFO);
        var tarefa = new ContratoKanbanTask(); tarefa.setIdTask(40L); tarefa.setContrato(contrato); tarefa.setRaia(raia);
        tarefa.setResponsaveis(List.of(usuario(2L), usuario(4L))); tarefa.setTitulo("Análise CFO");
        when(contratos.buscarAbertasComPrazoAte(any())).thenReturn(List.of(tarefa));
        for (LocalDate prazo : List.of(hoje, hoje.minusDays(1))) {
            tarefa.setDataFim(prazo); service.processar(hoje);
            for (Long id : List.of(2L, 4L)) {
                verify(avisosInternos).notificar(eq(id), argThat(lista -> lista.size() == 1
                        && lista.getFirst().prazo().equals(prazo) && lista.getFirst().link().endsWith("&servico=CFO")), eq(hoje));
            }
            verifyNoMoreInteractions(avisosInternos);
            clearInvocations(avisosInternos);
        }
    }
    @Test void preVendasTambemNotificaDiretorEUmErroNaoImpedeOutroDestinatario() {
        when(usuarios.buscarDiretoresComerciaisAtivos()).thenReturn(List.of(usuario(1L), usuario(2L)));
        when(comerciais.findAbertasComPrazoAte(any())).thenReturn(List.of(comercial(20L, 0, true)));
        when(contratos.buscarAbertasComPrazoAte(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("Falha de teste")).when(envio).enviar(eq(1L), anyList(), any());
        service.processar(hoje);
        verify(envio).enviar(eq(2L), argThat(a -> a.getFirst().link().contains("pipeline-pre-vendas?funilId=10")), eq(hoje));
    }
    @Test void mostraServicoDaTarefaERecuperaServicoDePropostaLegada() {
        when(usuarios.buscarDiretoresComerciaisAtivos()).thenReturn(List.of());
        when(comerciais.findAbertasComPrazoAte(any())).thenReturn(List.of());
        var proposta = new Proposta(); proposta.setServico(ServicoComercial.VALUATION);
        var contrato = new Contrato(); contrato.setIdContrato(30L); contrato.setResponsavel(usuario(3L)); contrato.setProposta(proposta);
        var tarefa = new ContratoKanbanTask(); tarefa.setIdTask(40L); tarefa.setContrato(contrato); tarefa.setDataFim(hoje);
        when(contratos.buscarAbertasComPrazoAte(any())).thenReturn(List.of(tarefa));
        service.processar(hoje);
        verify(envio).enviar(eq(3L), argThat(a -> "VALUATION".equals(a.getFirst().servicos())), eq(hoje));
        clearInvocations(envio);
        var contabilidade = new PropostaServico(ServicoComercial.CONTABILIDADE, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN);
        var software = new PropostaServico(ServicoComercial.DESENVOLVIMENTO_SOFTWARE, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN);
        proposta.setServicos(List.of(contabilidade, software)); service.processar(hoje);
        verify(envio).enviar(eq(3L), argThat(a -> "CONTABILIDADE".equals(a.getFirst().servicos())), eq(hoje));
        clearInvocations(envio);
        var raia = new ContratoKanbanRaia(); raia.setServico(ServicoComercial.DESENVOLVIMENTO_SOFTWARE); tarefa.setRaia(raia);
        contrato.getResponsaveisPorServico().put(ServicoComercial.DESENVOLVIMENTO_SOFTWARE, usuario(6L));
        service.processar(hoje);
        verify(envio).enviar(eq(6L), argThat(a -> "DESENVOLVIMENTO SOFTWARE".equals(a.getFirst().servicos())
                && a.getFirst().link().contains("&servico=DESENVOLVIMENTO_SOFTWARE")), eq(hoje));
    }

    private PipelineVendasTarefa comercial(Long id, int dias, boolean preVendas) {
        var funil = new PipelineVendasFunil(); funil.setIdFunil(10L); funil.setTipo(preVendas ? "PRE_VENDAS" : "VENDAS");
        var negocio = new PipelineVendasNegocio(); negocio.setIdNegocio(11L); negocio.setFunil(funil);
        negocio.setNomeEmpresa("Empresa comercial"); negocio.setServicoInteresse("VALUATION");
        var tarefa = new PipelineVendasTarefa(); tarefa.setIdTarefa(id); tarefa.setTitulo("Atividade " + id);
        tarefa.setNegocio(negocio); tarefa.setPrazo(hoje.plusDays(dias)); tarefa.setResponsaveis(List.of(usuario(4L), usuario(5L)));
        return tarefa;
    }
    private Usuario usuario(Long id) { var u = new Usuario(); u.setId(id); u.setNomeCompleto("Pessoa " + id); return u; }
}
