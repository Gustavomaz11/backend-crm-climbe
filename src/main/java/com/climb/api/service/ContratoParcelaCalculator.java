package com.climb.api.service;

import com.climb.api.model.Proposta;
import com.climb.api.model.PropostaReajuste;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ContratoParcelaCalculator {

    private static final int HORIZONTE_RECORRENCIA_INDETERMINADA = 24;

    public record ParcelaPlanejada(int numero, LocalDate competencia, LocalDate vencimento, BigDecimal valor) {}

    public List<ParcelaPlanejada> calcular(Proposta proposta, LocalDate dataAprovacao) {
        if (proposta == null || proposta.getServico() == null || proposta.getValuation() == null) {
            return List.of();
        }

        return proposta.getServico().recorrente()
                ? calcularRecorrentes(proposta, dataAprovacao)
                : calcularParcelamento(proposta, dataAprovacao);
    }

    private List<ParcelaPlanejada> calcularRecorrentes(Proposta proposta, LocalDate aprovacao) {
        int quantidade = proposta.getRecorrenciaMeses() == null || proposta.getRecorrenciaMeses() == 0
                ? HORIZONTE_RECORRENCIA_INDETERMINADA
                : proposta.getRecorrenciaMeses();
        return parcelasRecorrentes(proposta, aprovacao, quantidade);
    }

    private List<ParcelaPlanejada> calcularParcelamento(Proposta proposta, LocalDate aprovacao) {
        int quantidade = proposta.getQuantidadeParcelas() == null ? 1 : proposta.getQuantidadeParcelas();
        return parcelasIguais(proposta, aprovacao, quantidade);
    }

    private List<ParcelaPlanejada> parcelasIguais(Proposta proposta, LocalDate aprovacao, int quantidade) {
        BigDecimal valorBase = valorParcelaBase(proposta.getValuation(), quantidade);
        BigDecimal ultima = proposta.getValuation().subtract(valorBase.multiply(BigDecimal.valueOf(quantidade - 1L)));
        List<ParcelaPlanejada> parcelas = new ArrayList<>();
        for (int indice = 0; indice < quantidade; indice++) {
            parcelas.add(parcela(indice + 1, competenciaInicial(proposta, aprovacao).plusMonths(indice), aprovacao,
                    indice == quantidade - 1 ? ultima : valorBase));
        }
        return parcelas;
    }

    private List<ParcelaPlanejada> parcelasRecorrentes(Proposta proposta, LocalDate aprovacao, int quantidade) {
        List<PropostaReajuste> reajustes = proposta.getReajustes() == null ? List.of() : proposta.getReajustes().stream()
                .sorted(Comparator.comparing(PropostaReajuste::getMesVigencia))
                .toList();
        List<ParcelaPlanejada> parcelas = new ArrayList<>();
        BigDecimal valorBase = valorParcelaBase(proposta.getValuation(), quantidade);
        BigDecimal ultimaParcelaBase = proposta.getValuation()
                .subtract(valorBase.multiply(BigDecimal.valueOf(quantidade - 1L)));
        BigDecimal valorAtual = valorBase;
        boolean houveReajuste = false;
        for (int mes = 1; mes <= quantidade; mes++) {
            int numero = mes;
            BigDecimal reajusteDoMes = reajustes.stream()
                    .filter(reajuste -> reajuste.getMesVigencia() != null && reajuste.getMesVigencia() == numero)
                    .map(PropostaReajuste::getValor)
                    .findFirst()
                    .orElse(null);
            if (reajusteDoMes != null) {
                valorAtual = reajusteDoMes;
                houveReajuste = true;
            }
            BigDecimal valorParcela = mes == quantidade && !houveReajuste ? ultimaParcelaBase : valorAtual;
            LocalDate competencia = competenciaInicial(proposta, aprovacao).plusMonths(mes - 1L);
            parcelas.add(parcela(numero, competencia, aprovacao, valorParcela));
        }
        return parcelas;
    }

    private BigDecimal valorParcelaBase(BigDecimal valorTotal, int quantidade) {
        return valorTotal.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.DOWN);
    }

    private LocalDate competenciaInicial(Proposta proposta, LocalDate aprovacao) {
        LocalDate inicio = proposta.getMesInicio();
        if (inicio == null) return aprovacao.withDayOfMonth(1);
        return inicio.withDayOfMonth(1);
    }

    private ParcelaPlanejada parcela(int numero, LocalDate competencia, LocalDate aprovacao, BigDecimal valor) {
        YearMonth mes = YearMonth.from(competencia);
        int dia = Math.min(aprovacao.getDayOfMonth(), mes.lengthOfMonth());
        return new ParcelaPlanejada(numero, competencia.withDayOfMonth(1), mes.atDay(dia), valor.setScale(2, RoundingMode.HALF_UP));
    }
}
