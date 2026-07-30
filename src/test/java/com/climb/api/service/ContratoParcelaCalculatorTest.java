package com.climb.api.service;

import com.climb.api.model.Proposta;
import com.climb.api.model.PropostaReajuste;
import com.climb.api.model.enums.ServicoComercial;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContratoParcelaCalculatorTest {

    private final ContratoParcelaCalculator calculator = new ContratoParcelaCalculator();

    @Test
    void deveAplicarReajusteNaCompetenciaInformadaParaServicoRecorrente() {
        Proposta proposta = proposta(ServicoComercial.BPO, "1000.00");
        proposta.setMesInicio(LocalDate.of(2026, 1, 1));
        proposta.setRecorrenciaMeses(3);
        proposta.setReajustes(List.of(reajuste(2, "1500.00")));

        List<ContratoParcelaCalculator.ParcelaPlanejada> parcelas =
                calculator.calcular(proposta, LocalDate.of(2026, 1, 31));

        assertEquals(List.of(
                new BigDecimal("1000.00"),
                new BigDecimal("1500.00"),
                new BigDecimal("1500.00")
        ), parcelas.stream().map(ContratoParcelaCalculator.ParcelaPlanejada::valor).toList());
        assertEquals(LocalDate.of(2026, 2, 28), parcelas.get(1).vencimento());
    }

    @Test
    void deveDividirValorNaoRecorrenteEmParcelasIguaisSemPerderCentavos() {
        Proposta proposta = proposta(ServicoComercial.VALUATION, "1000.00");
        proposta.setQuantidadeParcelas(3);
        proposta.setParcelasIguais(true);

        List<ContratoParcelaCalculator.ParcelaPlanejada> parcelas =
                calculator.calcular(proposta, LocalDate.of(2026, 7, 10));

        assertEquals(List.of(
                new BigDecimal("333.33"),
                new BigDecimal("333.33"),
                new BigDecimal("333.34")
        ), parcelas.stream().map(ContratoParcelaCalculator.ParcelaPlanejada::valor).toList());
    }

    private Proposta proposta(ServicoComercial servico, String valor) {
        Proposta proposta = new Proposta();
        proposta.setServico(servico);
        proposta.setValuation(new BigDecimal(valor));
        proposta.setQuantidadeParcelas(1);
        proposta.setParcelasIguais(true);
        proposta.setReajustes(List.of());
        return proposta;
    }

    private PropostaReajuste reajuste(int mes, String valor) {
        PropostaReajuste reajuste = new PropostaReajuste();
        reajuste.setMesVigencia(mes);
        reajuste.setValor(new BigDecimal(valor));
        return reajuste;
    }
}
