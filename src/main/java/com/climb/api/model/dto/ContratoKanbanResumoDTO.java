package com.climb.api.model.dto;

import com.climb.api.model.enums.ServicoComercial;
import java.util.List;

public record ContratoKanbanResumoDTO(Long id, String empresaNome, String urlPdf, String status,
        boolean equipeConfigurada, ServicoComercial servico, List<ServicoResumo> servicos) {
    public record ServicoResumo(ServicoComercial servico) {}
}
