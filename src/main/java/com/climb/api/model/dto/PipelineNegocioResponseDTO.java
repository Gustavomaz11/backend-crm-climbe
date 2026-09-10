package com.climb.api.model.dto;

import com.climb.api.model.enums.PipelineVendasResultado;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PipelineNegocioResponseDTO(
        Long id,
        Long funilId,
        String funilNome,
        Long empresaId,
        String nomeEmpresa,
        String nomeContato,
        String telefone,
        String email,
        Long responsavelId,
        String responsavelNome,
        Long etapaId,
        String etapaCodigo,
        String etapaNome,
        LocalDateTime dataReuniao,
        String origemNegocio,
        String estrategiaComercial,
        String servicoInteresse,
        List<String> servicosInteresse,
        BigDecimal valorEstimadoProposta,
        String observacoes,
        PipelineVendasResultado resultado,
        Long motivoPerdaId,
        String motivoPerdaNome,
        String observacaoPerda,
        LocalDateTime encerradoEm,
        Long contratoId,
        boolean possuiProposta,
        boolean propostaAjustesPendentes,
        LocalDateTime criadoEm,
        LocalDateTime ultimaMovimentacaoEm,
        String tipoFunil, Long pessoaId, Long campanhaOrigemId, String campanhaOrigemNome, Long preVendaOrigemId, Long negocioGeradoId, java.util.Set<com.climb.api.model.PipelineTag> tags, java.util.Map<Long, String> campos, String cadenciaStatus
) {
    public PipelineNegocioResponseDTO(Long id, Long funilId, String funilNome, Long empresaId, String nomeEmpresa, String nomeContato, String telefone, String email, Long responsavelId, String responsavelNome, Long etapaId, String etapaCodigo, String etapaNome, LocalDateTime dataReuniao, String origemNegocio, String estrategiaComercial, String servicoInteresse, List<String> servicosInteresse, BigDecimal valorEstimadoProposta, String observacoes, PipelineVendasResultado resultado, Long motivoPerdaId, String motivoPerdaNome, String observacaoPerda, LocalDateTime encerradoEm, Long contratoId, boolean possuiProposta, boolean propostaAjustesPendentes, LocalDateTime criadoEm, LocalDateTime ultimaMovimentacaoEm) {
        this(id, funilId, funilNome, empresaId, nomeEmpresa, nomeContato, telefone, email, responsavelId, responsavelNome, etapaId, etapaCodigo, etapaNome, dataReuniao, origemNegocio, estrategiaComercial, servicoInteresse, servicosInteresse, valorEstimadoProposta, observacoes, resultado, motivoPerdaId, motivoPerdaNome, observacaoPerda, encerradoEm, contratoId, possuiProposta, propostaAjustesPendentes, criadoEm, ultimaMovimentacaoEm, "VENDAS", null, null, null, null, null, java.util.Set.of(), java.util.Map.of(), null);
    }

}
