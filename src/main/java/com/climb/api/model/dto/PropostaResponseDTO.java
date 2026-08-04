package com.climb.api.model.dto;

import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.enums.RevisaoDocumentoStatus;
import com.climb.api.model.enums.ServicoComercial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record PropostaResponseDTO(
        Long idProposta,
        Long empresaId,
        Long negocioId,
        Long usuarioId,
        String url,
        BigDecimal valuation,
        PropostaStatus status,
        RevisaoDocumentoStatus revisaoStatus,
        LocalDate dataCriacao,
        ServicoComercial servico,
        LocalDate mesInicio,
        Integer recorrenciaMeses,
        Integer quantidadeParcelas,
        Boolean parcelasIguais,
        BigDecimal comissaoTecnicoPercentual,
        BigDecimal comissaoComercialPercentual,
        Set<Long> equipeTecnicaIds,
        Set<Long> equipeComercialIds,
        List<PropostaReajusteDTO> reajustes,
        String observacoes
) {
}
