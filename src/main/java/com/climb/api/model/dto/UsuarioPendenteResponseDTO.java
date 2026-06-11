package com.climb.api.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsuarioPendenteResponseDTO {
    private String origem;
    private Long id;
    private String nomeCompleto;
    private String email;
    private String avatarUrl;
    private String cpf;
    private String contato;
    private String cargoNome;
    private LocalDateTime criadoEm;
    private LocalDateTime expiraEm;
}
