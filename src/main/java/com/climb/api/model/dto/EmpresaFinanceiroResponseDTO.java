package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EmpresaFinanceiroResponseDTO(
        Long empresaId,
        BigDecimal proximoRecebimentoValor,
        LocalDate proximoRecebimentoData,
        List<ServicoContratadoDTO> servicos
) {
    public record ServicoContratadoDTO(
            Long contratoId,
            ServicoComercial servico,
            String situacao,
            BigDecimal valorTotal,
            BigDecimal proximoRecebimentoValor,
            LocalDate proximoRecebimentoData,
            List<ParcelaDTO> parcelas,
            List<FuncionarioDTO> funcionarios
    ) {}

    public record ParcelaDTO(Long id, Integer numero, LocalDate vencimento, BigDecimal valor, String status) {}
    public record FuncionarioDTO(Long id, String nome, String email) {}
}
