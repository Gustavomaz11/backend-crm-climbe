package com.climb.api.service;

import com.climb.api.model.dto.PipelineEtapaConfiguracaoRequestDTO;
import com.climb.api.model.dto.PipelineFunilRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
class PipelineFunilValidator {
    private static final Set<String> CAMPOS_PERMITIDOS = Set.of(
            "nomeEmpresa", "nomeContato", "telefone", "email", "responsavelId", "dataReuniao",
            "origemNegocio", "estrategiaComercial", "servicoInteresse", "valorEstimadoProposta", "observacoes"
    );

    void validar(PipelineFunilRequestDTO dto) {
        if (dto.etapas().stream().noneMatch(PipelineEtapaConfiguracaoRequestDTO::ativo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O funil precisa possuir ao menos uma etapa ativa");
        }
        Set<String> nomes = new HashSet<>();
        for (PipelineEtapaConfiguracaoRequestDTO etapa : dto.etapas()) validarEtapa(etapa, nomes);
    }

    private void validarEtapa(PipelineEtapaConfiguracaoRequestDTO etapa, Set<String> nomes) {
        if (etapa.sucesso() && etapa.perda()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uma etapa não pode representar sucesso e perda ao mesmo tempo");
        }
        if (!nomes.add(etapa.nome().trim().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não repita nomes de etapas no mesmo funil");
        }
        List<String> camposInvalidos = (etapa.camposObrigatorios() == null ? List.<String>of() : etapa.camposObrigatorios())
                .stream().filter(campo -> !CAMPOS_PERMITIDOS.contains(campo)).toList();
        if (!camposInvalidos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campos obrigatórios inválidos: " + String.join(", ", camposInvalidos));
        }
    }
}
