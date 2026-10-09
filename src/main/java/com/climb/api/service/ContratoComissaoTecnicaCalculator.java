package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.ContratoRateioTecnicoResponseDTO.Servico;
import com.climb.api.model.enums.ServicoComercial;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.YearMonth;
import java.util.*;

@Component
public class ContratoComissaoTecnicaCalculator {
    private record Base(ServicoComercial servico, BigDecimal valor, BigDecimal percentual) {}

    public List<Servico> calcular(Contrato contrato, YearMonth competencia) {
        Proposta proposta = contrato.getProposta();
        if (proposta == null) return List.of();
        List<Base> bases = bases(proposta);
        if (bases.isEmpty()) return List.of();
        Map<ServicoComercial, BigDecimal> recebimentos = new EnumMap<>(ServicoComercial.class);
        bases.forEach(b -> recebimentos.put(b.servico(), BigDecimal.ZERO));
        contrato.getParcelas().stream().filter(p -> {
            var mes = p.getCompetencia() != null ? p.getCompetencia() : p.getVencimento();
            return mes != null && competencia.equals(YearMonth.from(mes));
        }).forEach(p -> distribuirRecebimento(proposta, p, bases, recebimentos));
        return bases.stream().map(b -> {
            BigDecimal recebido = recebimentos.get(b.servico()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal comissao = recebido.multiply(b.percentual()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return new Servico(b.servico(), recebido, b.percentual(), comissao);
        }).toList();
    }

    private List<Base> bases(Proposta proposta) {
        if (!proposta.getServicos().isEmpty()) return proposta.getServicos().stream().map(s ->
                new Base(s.getServico(), s.getValor(), percentual(s.getComissaoTecnicoPercentual()))).toList();
        if (proposta.getServico() == null || proposta.getValuation() == null) return List.of();
        return List.of(new Base(proposta.getServico(), proposta.getValuation(), percentual(proposta.getComissaoTecnicoPercentual())));
    }
    private BigDecimal percentual(BigDecimal valor) { return valor == null ? new BigDecimal("30") : valor; }

    private void distribuirRecebimento(Proposta proposta, ContratoParcela parcela, List<Base> bases,
            Map<ServicoComercial, BigDecimal> recebimentos) {
        var personalizados = proposta.getRecebimentosPorServico().stream()
                .filter(r -> Objects.equals(r.getNumero(), parcela.getNumero())).toList();
        if (!personalizados.isEmpty()) {
            personalizados.forEach(r -> recebimentos.merge(r.getServico(), r.getValor(), BigDecimal::add));
            return;
        }
        BigDecimal totalServicos = bases.stream().map(Base::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalServicos.signum() <= 0) return;
        BigDecimal restante = parcela.getValor();
        for (int i = 0; i < bases.size(); i++) {
            Base base = bases.get(i);
            BigDecimal valor = i == bases.size() - 1 ? restante : parcela.getValor().multiply(base.valor())
                    .divide(totalServicos, 2, RoundingMode.DOWN);
            restante = restante.subtract(valor);
            recebimentos.merge(base.servico(), valor, BigDecimal::add);
        }
    }
}
