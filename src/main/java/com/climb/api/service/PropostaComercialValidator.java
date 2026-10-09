package com.climb.api.service;

import com.climb.api.model.dto.PropostaComercialRequestDTO;
import com.climb.api.model.dto.PropostaRecebimentoDTO;
import com.climb.api.model.enums.ServicoComercial;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PropostaComercialValidator {
    private PropostaComercialValidator() {}

    static void validar(PropostaComercialRequestDTO config, BigDecimal total) {
        if (config == null || config.servico() == null) throw new IllegalArgumentException("Selecione o serviço da proposta.");
        int recorrencia = config.recorrenciaMeses() == null ? 0 : config.recorrenciaMeses();
        int quantidade = config.quantidadeParcelas() == null ? 1 : config.quantidadeParcelas();
        if (config.servico().recorrente() && (recorrencia < 0 || recorrencia > 24)) {
            throw new IllegalArgumentException("A recorrência deve estar entre 0 e 24 meses.");
        }
        if (quantidade < 1 || quantidade > 24) throw new IllegalArgumentException("Informe entre 1 e 24 recebimentos.");
        if (config.servicos() != null) {
            if (config.servicos().isEmpty()) throw new IllegalArgumentException("Adicione pelo menos um serviço à proposta.");
            Set<ServicoComercial> selecionados = new HashSet<>();
            BigDecimal soma = BigDecimal.ZERO;
            for (var servico : config.servicos()) {
                if (servico == null || servico.servico() == null || !selecionados.add(servico.servico())) {
                    throw new IllegalArgumentException("Selecione cada serviço apenas uma vez.");
                }
                validarValor(servico.valor());
                validarComissao(servico.comissaoTecnicoPercentual());
                validarComissao(servico.comissaoComercialPercentual());
                soma = soma.add(servico.valor());
            }
            if (soma.compareTo(total) != 0) throw new IllegalArgumentException("A soma dos valores dos serviços deve ser igual ao valor total da proposta.");
            if (config.recebimentos() == null) throw new IllegalArgumentException("Configure os recebimentos da proposta.");
        }
        if (config.recebimentos() != null) {
            if (config.quantidadeParcelas() == null) throw new IllegalArgumentException("Informe a quantidade de recebimentos da proposta.");
            if (config.mesInicio() == null) throw new IllegalArgumentException("Informe o mês de início dos recebimentos.");
            if (config.reajustes() != null && !config.reajustes().isEmpty()) {
                throw new IllegalArgumentException("Use os valores dos recebimentos para personalizar esta proposta.");
            }
            validarRecebimentos(config.recebimentos(), quantidade, total);
            PropostaRecebimentoServicoValidator.validar(config.recebimentos(), config.servicos());
        }
    }

    static void validarRecebimentos(List<PropostaRecebimentoDTO> recebimentos, int quantidade, BigDecimal total) {
        if (quantidade < 1 || quantidade > 24 || recebimentos.size() != quantidade) {
            throw new IllegalArgumentException("Configure todos os recebimentos da proposta, entre 1 e 24 parcelas.");
        }
        Set<Integer> numeros = new HashSet<>();
        BigDecimal soma = BigDecimal.ZERO;
        for (var recebimento : recebimentos) {
            if (recebimento == null || recebimento.numero() == null || recebimento.numero() < 1
                    || recebimento.numero() > quantidade || !numeros.add(recebimento.numero())) {
                throw new IllegalArgumentException("Informe cada número de recebimento apenas uma vez, em sequência.");
            }
            validarValor(recebimento.valor());
            soma = soma.add(recebimento.valor());
        }
        if (soma.compareTo(total) != 0) throw new IllegalArgumentException("A soma dos recebimentos deve ser igual ao valor total da proposta.");
    }

    private static void validarValor(BigDecimal valor) {
        validarValor(valor, false);
    }

    static void validarValor(BigDecimal valor, boolean permiteZero) {
        if (valor == null || valor.signum() < 0 || (!permiteZero && valor.signum() == 0) || valor.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new IllegalArgumentException(permiteZero ? "Informe valores a partir de zero para os serviços de cada recebimento."
                    : "Informe um valor maior que zero para cada serviço e recebimento.");
        }
        try { valor.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Informe os valores com no máximo duas casas decimais."); }
    }

    private static void validarComissao(BigDecimal percentual) {
        if (percentual == null) return;
        if (percentual.signum() < 0 || percentual.compareTo(new BigDecimal("100")) > 0 || percentual.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Informe comissões entre 0% e 100%, com no máximo duas casas decimais.");
        }
    }
}
