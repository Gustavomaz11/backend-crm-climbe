package com.climb.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;

public record EmpresaRequestDTO(
        @NotBlank String razaoSocial,
        @NotBlank String nomeFantasia,
        @CNPJ @NotBlank String cnpj,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        String telefone,
        String email,
        String representanteNome,
        String representanteCpf,
        String representanteContato,
        String tipoEmpresa,
        java.util.Map<String, Object> dadosCadastrais
) {
}
