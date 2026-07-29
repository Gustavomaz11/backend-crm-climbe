package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineDashboardResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineDashboardCalculatorTest {
    private final PipelineDashboardCalculator calculator = new PipelineDashboardCalculator();

    @Test
    void deveCalcularConversaoValoresPerdasEEstagnacao() {
        PipelineVendasFunil funil = funil();
        Usuario responsavel = usuario();
        PipelineVendasEtapa aberta = etapa(1L, "Diagnóstico", PipelineVendasResultado.ABERTO, funil);
        PipelineVendasEtapa ganha = etapa(2L, "Fechado", PipelineVendasResultado.GANHO, funil);
        PipelineVendasEtapa perdida = etapa(3L, "Perdido", PipelineVendasResultado.PERDIDO, funil);
        PipelineVendasNegocio estagnado = negocio(1L, aberta, responsavel, PipelineVendasResultado.ABERTO, "100");
        estagnado.setUltimaMovimentacaoEm(LocalDateTime.now().minusDays(20));
        PipelineVendasNegocio ganho = negocio(2L, ganha, responsavel, PipelineVendasResultado.GANHO, "200");
        ganho.setContrato(new Contrato());
        ganho.setEncerradoEm(ganho.getCriadoEm().plusDays(4));
        PipelineVendasNegocio perdido = negocio(3L, perdida, responsavel, PipelineVendasResultado.PERDIDO, "300");
        PipelineMotivoPerda motivo = new PipelineMotivoPerda();
        motivo.setIdMotivo(5L);
        motivo.setNome("Sem orçamento");
        perdido.setMotivoPerda(motivo);
        perdido.setEncerradoEm(perdido.getCriadoEm().plusDays(6));

        PipelineDashboardResponseDTO resultado = calculator.calcular(
                List.of(estagnado, ganho, perdido),
                List.of(movimento(estagnado, aberta), movimento(ganho, ganha), movimento(perdido, perdida)),
                2,
                new PipelineDashboardResponseDTO.OpcoesFiltro(List.of(), List.of(), List.of())
        );

        assertEquals(1, resultado.resumo().negociosAbertos());
        assertEquals(1, resultado.resumo().negociosGanhos());
        assertEquals(1, resultado.resumo().negociosPerdidos());
        assertEquals(50.0, resultado.resumo().taxaConversao());
        assertEquals(new BigDecimal("600"), resultado.resumo().valorTotalPropostas());
        assertEquals(new BigDecimal("200"), resultado.resumo().valorContratosFechados());
        assertEquals(5.0, resultado.resumo().tempoMedioFechamentoDias());
        assertEquals(1, resultado.resumo().negociosEstagnados());
        assertEquals(2, resultado.resumo().tarefasAtrasadas());
        assertEquals("Sem orçamento", resultado.principaisMotivosPerda().getFirst().motivo());
    }

    private PipelineVendasNegocio negocio(Long id,
                                           PipelineVendasEtapa etapa,
                                           Usuario responsavel,
                                           PipelineVendasResultado resultado,
                                           String valor) {
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(id);
        negocio.setNomeEmpresa("Empresa " + id);
        negocio.setFunil(etapa.getFunil());
        negocio.setEtapa(etapa);
        negocio.setResponsavel(responsavel);
        negocio.setResultado(resultado);
        negocio.setEstrategiaComercial("Consultiva");
        negocio.setValorEstimadoProposta(new BigDecimal(valor));
        negocio.setCriadoEm(LocalDateTime.now().minusDays(10));
        negocio.setUltimaMovimentacaoEm(LocalDateTime.now().minusDays(1));
        return negocio;
    }

    private PipelineVendasMovimentacaoEtapa movimento(PipelineVendasNegocio negocio, PipelineVendasEtapa etapa) {
        PipelineVendasMovimentacaoEtapa movimento = new PipelineVendasMovimentacaoEtapa();
        movimento.setNegocio(negocio);
        movimento.setEtapa(etapa);
        movimento.setEntradaEm(LocalDateTime.now().minusDays(2));
        movimento.setSaidaEm(LocalDateTime.now().minusDays(1));
        return movimento;
    }

    private PipelineVendasFunil funil() {
        PipelineVendasFunil funil = new PipelineVendasFunil();
        funil.setIdFunil(1L);
        funil.setNome("Funil Comercial");
        return funil;
    }

    private PipelineVendasEtapa etapa(Long id, String nome, PipelineVendasResultado resultado, PipelineVendasFunil funil) {
        PipelineVendasEtapa etapa = new PipelineVendasEtapa();
        etapa.setIdEtapa(id);
        etapa.setNome(nome);
        etapa.setResultado(resultado);
        etapa.setFunil(funil);
        etapa.setTempoMaximoPermanenciaDias(14);
        return etapa;
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNomeCompleto("Responsável");
        return usuario;
    }
}
