package com.climb.api.model.dto;

import java.util.List;

public record ContratoEquipeResponseDTO(Long contratoId, boolean configurada, boolean lider,
        UsuarioResumoDTO responsavel, List<UsuarioResumoDTO> membros, List<UsuarioResumoDTO> usuariosDisponiveis,
        List<Temporario> temporarios) {
    public record TarefaResumo(Long id, String titulo) {}
    public record Temporario(UsuarioResumoDTO usuario, List<TarefaResumo> tarefas) {}
}
