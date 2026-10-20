package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ContratoServicosTest {
    @Test void usaListaOrdenadaERecuperaServicoExplicitoDoContratoLegado() {
        var contrato = new Contrato(); contrato.setServico(ServicoComercial.CFO);
        var proposta = new Proposta(); proposta.setServico(ServicoComercial.VALUATION); contrato.setProposta(proposta);
        assertThat(ContratoServicos.listar(contrato)).containsExactly(ServicoComercial.CFO);
        proposta.setServicos(List.of(
                new PropostaServico(ServicoComercial.CONTABILIDADE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)));
        assertThat(ContratoServicos.listar(contrato)).containsExactly(ServicoComercial.CONTABILIDADE, ServicoComercial.CFO);
        var tarefa = new ContratoKanbanTask(); tarefa.setContrato(contrato);
        assertThat(ContratoServicos.daTarefa(tarefa)).isEqualTo(ServicoComercial.CONTABILIDADE);
        var raia = new ContratoKanbanRaia(); raia.setServico(ServicoComercial.CFO); tarefa.setRaia(raia);
        assertThat(ContratoServicos.daTarefa(tarefa)).isEqualTo(ServicoComercial.CFO);
    }
}
