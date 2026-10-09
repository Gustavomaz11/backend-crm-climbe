package com.climb.api.service;

import com.climb.api.model.dto.PropostaRecebimentoDTO;
import com.climb.api.model.dto.PropostaServicoDTO;
import com.climb.api.model.enums.ServicoComercial;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class PropostaRecebimentoServicoValidator {
    private PropostaRecebimentoServicoValidator() {}

    static void validar(List<PropostaRecebimentoDTO> recebimentos, List<PropostaServicoDTO> servicos) {
        if (recebimentos.stream().noneMatch(item -> item.servicos() != null)) return;
        if (servicos == null || servicos.isEmpty()) throw new IllegalArgumentException("Selecione os serviços antes de distribuir os recebimentos.");
        Map<ServicoComercial, BigDecimal> totais = new EnumMap<>(ServicoComercial.class);
        servicos.forEach(item -> totais.put(item.servico(), BigDecimal.ZERO));
        recebimentos.forEach(recebimento -> validarMes(recebimento, totais));
        for (var servico : servicos) {
            if (totais.get(servico.servico()).compareTo(servico.valor()) != 0) {
                throw new IllegalArgumentException("A soma dos recebimentos de " + servico.servico().name() + " deve ser igual ao valor destinado a esse serviço.");
            }
        }
    }

    private static void validarMes(PropostaRecebimentoDTO recebimento, Map<ServicoComercial, BigDecimal> totais) {
        if (recebimento.servicos() == null || recebimento.servicos().size() != totais.size()) {
            throw new IllegalArgumentException("Informe o valor de todos os serviços em cada recebimento.");
        }
        var selecionados = new HashSet<ServicoComercial>();
        BigDecimal soma = BigDecimal.ZERO;
        for (var item : recebimento.servicos()) {
            if (item == null || item.servico() == null || !totais.containsKey(item.servico()) || !selecionados.add(item.servico())) {
                throw new IllegalArgumentException("Use somente os serviços da proposta, sem repeti-los no recebimento.");
            }
            PropostaComercialValidator.validarValor(item.valor(), true);
            soma = soma.add(item.valor());
            totais.merge(item.servico(), item.valor(), BigDecimal::add);
        }
        if (soma.compareTo(recebimento.valor()) != 0) {
            throw new IllegalArgumentException("O total do recebimento " + recebimento.numero() + " deve ser a soma dos valores dos seus serviços.");
        }
    }
}
