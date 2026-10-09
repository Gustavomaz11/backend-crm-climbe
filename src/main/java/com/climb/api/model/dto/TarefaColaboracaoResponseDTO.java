package com.climb.api.model.dto;

import java.util.List;

public record TarefaColaboracaoResponseDTO(List<TarefaAnexoResponseDTO> anexos, List<TarefaComentarioResponseDTO> comentarios) {}
