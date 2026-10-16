package com.climb.api.model.dto;

public record CnpjEmpresaResponseDTO(
        String cnpj, String razaoSocial, String nomeFantasia,
        String logradouro, String numero, String bairro, String cidade, String uf, String cep,
        String telefone, String email, String tipoEmpresa, java.util.Map<String, Object> dadosCadastrais,
        java.util.List<String> avisos
) {}
