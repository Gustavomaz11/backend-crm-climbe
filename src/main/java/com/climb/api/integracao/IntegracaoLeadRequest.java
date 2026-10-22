package com.climb.api.integracao;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CNPJ;

public record IntegracaoLeadRequest(
        @NotBlank @Size(max = 180) String nomeEmpresa,
        @NotBlank @CNPJ String cnpj,
        @NotBlank @Size(max = 180) String nomeContato,
        @Size(max = 30) String telefone,
        @Email @Size(max = 180) String email,
        @Min(0) @Max(value = 0, message = "Envie 0 para deixar o lead sem responsável") Long responsavelId,
        @NotBlank @Size(max = 120) String origemNegocio,
        @NotBlank @Size(max = 500) String estrategiaComercial,
        @Size(max = 5000) String observacoes
) {}
