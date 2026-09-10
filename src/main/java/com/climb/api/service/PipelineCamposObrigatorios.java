package com.climb.api.service;

import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.PipelineVendasEtapa;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

public final class PipelineCamposObrigatorios {
    private PipelineCamposObrigatorios() {}

    public static void validar(PipelineVendasNegocio negocio, PipelineVendasEtapa etapa) {
        List<String> ausentes = (etapa.getCamposObrigatorios() == null ? List.<String>of() : etapa.getCamposObrigatorios()).stream()
                .filter(campo -> !"servicoInteresse".equals(campo))
                .filter(campo -> campoAusente(negocio, campo))
                .toList();
        if (!ausentes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Preencha os campos obrigatórios da etapa " + etapa.getNome() + ": " + String.join(", ", ausentes));
        }
    }

    private static boolean campoAusente(PipelineVendasNegocio negocio, String campo) {
        return switch (campo) {
            case "nomeEmpresa" -> textoAusente(negocio.getNomeEmpresa());
            case "nomeContato" -> textoAusente(negocio.getNomeContato());
            case "telefone" -> textoAusente(negocio.getTelefone());
            case "email" -> textoAusente(negocio.getEmail());
            case "responsavelId" -> negocio.getResponsavel() == null;
            case "dataReuniao" -> negocio.getDataReuniao() == null;
            case "origemNegocio" -> textoAusente(negocio.getOrigemNegocio());
            case "estrategiaComercial" -> textoAusente(negocio.getEstrategiaComercial());
            case "valorEstimadoProposta" -> negocio.getValorEstimadoProposta() == null;
            case "observacoes" -> textoAusente(negocio.getObservacoes());
            default -> false;
        };
    }

    private static boolean textoAusente(String valor) {
        return valor == null || valor.isBlank();
    }

}
