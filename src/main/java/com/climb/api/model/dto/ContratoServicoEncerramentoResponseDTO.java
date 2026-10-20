package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;
import java.time.LocalDateTime;

public record ContratoServicoEncerramentoResponseDTO(Long contratoId, ServicoComercial servico,
        LocalDateTime encerradoEm, Long encerradoPorId) {}
