package com.climb.api.service;

import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.Usuario;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class PipelineTemplateRenderer {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    String renderizar(String modelo, PipelineVendasNegocio negocio, Usuario responsavel) {
        if (modelo == null || modelo.isBlank()) return null;
        String resultado = modelo;
        for (Map.Entry<String, String> variavel : variaveis(negocio, responsavel).entrySet()) {
            resultado = resultado.replace("{{" + variavel.getKey() + "}}", variavel.getValue());
        }
        return resultado;
    }

    private Map<String, String> variaveis(PipelineVendasNegocio negocio, Usuario responsavel) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("nome_pessoa", seguro(negocio.getNomeContato()));
        valores.put("nome_empresa", seguro(negocio.getNomeEmpresa()));
        valores.put("nome_responsavel", seguro(responsavel.getNomeCompleto()));
        valores.put("servico_interesse", seguro(negocio.getServicoInteresse()));
        valores.put("data_reuniao", negocio.getDataReuniao() == null ? "" : DATA.format(negocio.getDataReuniao()));
        return valores;
    }

    private String seguro(String valor) { return valor == null ? "" : valor; }
}
