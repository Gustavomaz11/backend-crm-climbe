package com.climb.api.model.dto;

import java.util.List;

public record ContratoKanbanBoardResponseDTO(
        Long contratoId,
        String contratoTitulo,
        boolean gestor,
        UsuarioResumoDTO responsavel,
        List<UsuarioResumoDTO> participantes,
        List<ContratoKanbanRaiaResponseDTO> raias
) {}
