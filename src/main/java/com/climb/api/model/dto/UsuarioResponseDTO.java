package com.climb.api.model.dto;

import lombok.Data;

@Data
public class UsuarioResponseDTO {
    private Long id;
    private String nomeCompleto;
    private String cpf;
    private String email;
    private String contato;
    private String situacao;
    private String cargoNome;
    private String fotoPerfil;
}
