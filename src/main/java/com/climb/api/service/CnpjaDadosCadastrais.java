package com.climb.api.service;

import java.util.*;

public final class CnpjaDadosCadastrais {
    private CnpjaDadosCadastrais() {}

    public static Map<String, Object> semHistoricos(Map<String, Object> data) {
        var result = new LinkedHashMap<String, Object>();
        if (data == null) return result;
        data.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (normalized.contains("history") || normalized.contains("historico") || normalized.contains("certificate") || normalized.equals("links")) return;
            result.put(key, limpar(value));
        });
        return result;
    }

    private static Object limpar(Object value) {
        if (value instanceof Map<?, ?> map) return semHistoricos(objeto(map));
        if (value instanceof List<?> list) return list.stream().map(CnpjaDadosCadastrais::limpar).toList();
        return value;
    }

    public static Map<String, Object> objeto(Object value) {
        var result = new LinkedHashMap<String, Object>();
        if (value instanceof Map<?, ?> map) map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    public static String tipoEmpresa(Map<String, Object> dados) {
        var company = objeto(dados.get("company"));
        if (Boolean.TRUE.equals(objeto(company.get("simei")).get("optant"))) return "MEI";
        var optante = objeto(company.get("simples")).get("optant");
        if (Boolean.TRUE.equals(optante)) return "SIMPLES_NACIONAL";
        return Boolean.FALSE.equals(optante) ? "NAO_OPTANTE" : "NAO_INFORMADO";
    }

    public static Map<String, Object> comDadosBasicos(com.climb.api.model.Empresa empresa) {
        var dados = semHistoricos(empresa.getDadosCadastrais());
        if (dados.isEmpty()) return dados;
        dados.put("taxId", empresa.getCnpj().replaceAll("\\D", ""));
        dados.put("alias", empresa.getNomeFantasia());
        var company = objeto(dados.get("company"));
        company.put("name", empresa.getRazaoSocial());
        dados.put("company", company);
        var endereco = objeto(dados.get("address"));
        endereco.put("street", empresa.getLogradouro()); endereco.put("number", empresa.getNumero());
        endereco.put("district", empresa.getBairro()); endereco.put("city", empresa.getCidade());
        endereco.put("state", empresa.getUf()); endereco.put("zip", empresa.getCep());
        dados.put("address", endereco);
        return dados;
    }
}
