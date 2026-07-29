package com.climb.api.service;

import com.climb.api.model.PipelineVendasMovimentacaoEtapa;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.dto.PipelineDashboardResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class PipelineDashboardCalculator {
    private static final int LIMITE_PADRAO_ESTAGNACAO_DIAS = 14;

    PipelineDashboardResponseDTO calcular(List<PipelineVendasNegocio> negocios,
                                          List<PipelineVendasMovimentacaoEtapa> movimentacoes,
                                          long tarefasAtrasadas,
                                          PipelineDashboardResponseDTO.OpcoesFiltro opcoes) {
        long abertos = contar(negocios, PipelineVendasResultado.ABERTO);
        long ganhos = contar(negocios, PipelineVendasResultado.GANHO);
        long perdidos = contar(negocios, PipelineVendasResultado.PERDIDO);
        List<PipelineDashboardResponseDTO.NegocioAtencao> estagnados = negociosEstagnados(negocios);
        PipelineDashboardResponseDTO.Resumo resumo = new PipelineDashboardResponseDTO.Resumo(
                abertos, ganhos, perdidos, taxa(ganhos, perdidos), valorPropostas(negocios),
                valorContratos(negocios), mediaFechamento(negocios), estagnados.size(), tarefasAtrasadas);
        return new PipelineDashboardResponseDTO(
                resumo,
                conversaoPorEtapa(negocios, movimentacoes),
                conversaoAgrupada(negocios, negocio -> negocio.getFunil().getNome()),
                conversaoAgrupada(negocios, PipelineVendasNegocio::getEstrategiaComercial),
                conversaoAgrupada(negocios, negocio -> negocio.getResponsavel().getNomeCompleto()),
                tempoPorEtapa(movimentacoes),
                motivosPerda(negocios, perdidos),
                estagnados,
                opcoes
        );
    }

    private long contar(List<PipelineVendasNegocio> negocios, PipelineVendasResultado resultado) {
        return negocios.stream().filter(negocio -> negocio.getResultado() == resultado).count();
    }

    private double taxa(long ganhos, long perdidos) {
        long encerrados = ganhos + perdidos;
        return encerrados == 0 ? 0 : arredondar(ganhos * 100.0 / encerrados);
    }

    private BigDecimal valorPropostas(List<PipelineVendasNegocio> negocios) {
        return negocios.stream().map(PipelineVendasNegocio::getValorEstimadoProposta)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valorContratos(List<PipelineVendasNegocio> negocios) {
        return negocios.stream()
                .filter(negocio -> negocio.getResultado() == PipelineVendasResultado.GANHO && negocio.getContrato() != null)
                .map(PipelineVendasNegocio::getValorEstimadoProposta).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double mediaFechamento(List<PipelineVendasNegocio> negocios) {
        return negocios.stream().filter(negocio -> negocio.getEncerradoEm() != null)
                .mapToDouble(negocio -> dias(negocio.getCriadoEm(), negocio.getEncerradoEm()))
                .average().stream().map(this::arredondar).findFirst().orElse(0);
    }

    private List<PipelineDashboardResponseDTO.Conversao> conversaoAgrupada(
            List<PipelineVendasNegocio> negocios,
            Function<PipelineVendasNegocio, String> agrupador) {
        return negocios.stream().collect(Collectors.groupingBy(agrupador)).entrySet().stream()
                .map(entry -> conversao(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(PipelineDashboardResponseDTO.Conversao::total).reversed())
                .toList();
    }

    private PipelineDashboardResponseDTO.Conversao conversao(String chave, List<PipelineVendasNegocio> negocios) {
        long ganhos = contar(negocios, PipelineVendasResultado.GANHO);
        long perdidos = contar(negocios, PipelineVendasResultado.PERDIDO);
        return new PipelineDashboardResponseDTO.Conversao(chave, negocios.size(), ganhos, perdidos, taxa(ganhos, perdidos));
    }

    private List<PipelineDashboardResponseDTO.Conversao> conversaoPorEtapa(
            List<PipelineVendasNegocio> negocios,
            List<PipelineVendasMovimentacaoEtapa> movimentacoes) {
        Map<Long, PipelineVendasNegocio> porId = negocios.stream()
                .collect(Collectors.toMap(PipelineVendasNegocio::getIdNegocio, Function.identity()));
        return movimentacoes.stream().collect(Collectors.groupingBy(mov -> mov.getEtapa().getIdEtapa())).values().stream()
                .map(lista -> {
                    List<PipelineVendasNegocio> passaram = lista.stream()
                            .map(mov -> porId.get(mov.getNegocio().getIdNegocio())).filter(Objects::nonNull).distinct().toList();
                    return conversao(lista.get(0).getEtapa().getNome(), passaram);
                })
                .sorted(Comparator.comparingLong(PipelineDashboardResponseDTO.Conversao::total).reversed())
                .toList();
    }

    private List<PipelineDashboardResponseDTO.TempoEtapa> tempoPorEtapa(
            List<PipelineVendasMovimentacaoEtapa> movimentacoes) {
        LocalDateTime agora = LocalDateTime.now();
        return movimentacoes.stream().collect(Collectors.groupingBy(mov -> mov.getEtapa().getIdEtapa())).values().stream()
                .map(lista -> {
                    PipelineVendasMovimentacaoEtapa primeira = lista.get(0);
                    double media = lista.stream().mapToDouble(mov -> dias(mov.getEntradaEm(),
                            mov.getSaidaEm() == null ? agora : mov.getSaidaEm())).average().orElse(0);
                    return new PipelineDashboardResponseDTO.TempoEtapa(
                            primeira.getEtapa().getIdEtapa(), primeira.getEtapa().getNome(),
                            primeira.getEtapa().getFunil().getNome(), arredondar(media));
                })
                .sorted(Comparator.comparingDouble(PipelineDashboardResponseDTO.TempoEtapa::mediaDias).reversed())
                .toList();
    }

    private List<PipelineDashboardResponseDTO.MotivoPerda> motivosPerda(
            List<PipelineVendasNegocio> negocios, long totalPerdidos) {
        return negocios.stream().filter(negocio -> negocio.getResultado() == PipelineVendasResultado.PERDIDO)
                .filter(negocio -> negocio.getMotivoPerda() != null)
                .collect(Collectors.groupingBy(PipelineVendasNegocio::getMotivoPerda, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new PipelineDashboardResponseDTO.MotivoPerda(
                        entry.getKey().getIdMotivo(), entry.getKey().getNome(), entry.getValue(),
                        totalPerdidos == 0 ? 0 : arredondar(entry.getValue() * 100.0 / totalPerdidos)))
                .sorted(Comparator.comparingLong(PipelineDashboardResponseDTO.MotivoPerda::quantidade).reversed())
                .toList();
    }

    private List<PipelineDashboardResponseDTO.NegocioAtencao> negociosEstagnados(
            List<PipelineVendasNegocio> negocios) {
        LocalDateTime agora = LocalDateTime.now();
        return negocios.stream().filter(negocio -> negocio.getResultado() == PipelineVendasResultado.ABERTO)
                .map(negocio -> atencao(negocio, agora))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(PipelineDashboardResponseDTO.NegocioAtencao::diasSemMovimentacao).reversed())
                .limit(30).toList();
    }

    private PipelineDashboardResponseDTO.NegocioAtencao atencao(PipelineVendasNegocio negocio, LocalDateTime agora) {
        int limite = negocio.getEtapa().getTempoMaximoPermanenciaDias() == null
                ? LIMITE_PADRAO_ESTAGNACAO_DIAS : negocio.getEtapa().getTempoMaximoPermanenciaDias();
        long dias = Duration.between(negocio.getUltimaMovimentacaoEm(), agora).toDays();
        if (dias <= limite) return null;
        return new PipelineDashboardResponseDTO.NegocioAtencao(
                negocio.getIdNegocio(), negocio.getNomeEmpresa(), negocio.getEtapa().getNome(),
                negocio.getFunil().getNome(), negocio.getResponsavel().getNomeCompleto(), dias, limite);
    }

    private double dias(LocalDateTime inicio, LocalDateTime fim) {
        return Duration.between(inicio, fim).toMinutes() / 1440.0;
    }

    private double arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
