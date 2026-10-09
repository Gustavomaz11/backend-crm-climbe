package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;
import java.math.BigDecimal;
import java.util.List;

public record ContratoRateioTecnicoResponseDTO(Long contratoId, String competencia, String competenciaPagamento, BigDecimal recebimentoTotal,
        BigDecimal comissaoTecnicaTotal, boolean registrado, List<Servico> servicos, List<Participante> participantes) {
    public record Servico(ServicoComercial servico, BigDecimal recebimento, BigDecimal percentual, BigDecimal comissao) {}
    public record Participante(UsuarioResumoDTO usuario, BigDecimal valor) {}
}
