package com.climb.api.model.dto;

import java.util.List;

public record ReuniaoDisponibilidadeDTO(List<Sala> salas, List<Participante> participantes, Boolean localDisponivel) {
    public record Sala(String email, Boolean disponivel) { }
    public record Participante(Long id, Boolean disponivel) { }
}
