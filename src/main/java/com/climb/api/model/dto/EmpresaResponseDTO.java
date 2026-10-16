package com.climb.api.model.dto;

public record EmpresaResponseDTO(
        Long id,
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
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
