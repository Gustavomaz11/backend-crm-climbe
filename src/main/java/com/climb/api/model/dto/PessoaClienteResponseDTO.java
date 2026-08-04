package com.climb.api.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PessoaClienteResponseDTO(
        Long id,
        String nome,
        String cpf,
        String email,
        String telefone,
        String cargo,
        String observacoes,
        Boolean ativo,
        List<PessoaEmpresaResumoDTO> empresas,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}
