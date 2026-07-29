package com.climb.api.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record PipelineDashboardResponseDTO(
        Resumo resumo,
        List<Conversao> conversaoPorEtapa,
        List<Conversao> conversaoPorFunil,
        List<Conversao> conversaoPorEstrategia,
        List<Conversao> conversaoPorResponsavel,
        List<TempoEtapa> tempoMedioPorEtapa,
        List<MotivoPerda> principaisMotivosPerda,
        List<NegocioAtencao> negociosEstagnados,
        OpcoesFiltro opcoes
) {
    public record Resumo(
            long negociosAbertos,
            long negociosGanhos,
            long negociosPerdidos,
            double taxaConversao,
            BigDecimal valorTotalPropostas,
            BigDecimal valorContratosFechados,
            double tempoMedioFechamentoDias,
            long negociosEstagnados,
            long tarefasAtrasadas
    ) {}

    public record Conversao(String chave, long total, long ganhos, long perdidos, double taxaConversao) {}

    public record TempoEtapa(Long etapaId, String etapa, String funil, double mediaDias) {}

    public record MotivoPerda(Long motivoId, String motivo, long quantidade, double percentual) {}

    public record NegocioAtencao(
            Long negocioId,
            String empresa,
            String etapa,
            String funil,
            String responsavel,
            long diasSemMovimentacao,
            int limiteDias
    ) {}

    public record OpcoesFiltro(
            List<String> estrategias,
            List<String> servicos,
            List<String> origens
    ) {}
}
