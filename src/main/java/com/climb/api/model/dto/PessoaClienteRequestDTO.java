package com.climb.api.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.util.Set;

public record PessoaClienteRequestDTO(
        @NotBlank @Size(max = 180) String nome,
        @CPF String cpf,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 50) String telefone,
        @Size(max = 120) String cargo,
        String observacoes,
        Boolean ativo,
        @NotEmpty(message = "Vincule a pessoa a pelo menos uma empresa") Set<Long> empresaIds
) {}
