package com.climb.api.integracao;

import jakarta.validation.constraints.*;

public record IntegracaoLeadRequest(
        @NotNull @Positive Long empresaId,
        @NotBlank @Size(max = 180) String nomeContato,
        @Size(max = 30) String telefone,
        @Email @Size(max = 254) String email,
        @NotNull @Positive Long responsavelId,
        @NotBlank @Size(max = 120) String origemNegocio,
        @NotBlank @Size(max = 500) String estrategiaComercial,
        @Size(max = 5000) String observacoes
) {}
