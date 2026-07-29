package com.climb.api.service;

import com.climb.api.model.dto.PipelineCadenciaEtapaRequestDTO;
import com.climb.api.model.dto.PipelineCampanhaRequestDTO;
import com.climb.api.model.enums.PipelineCadenciaTipo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class PipelineCampanhaValidator {
    void validar(PipelineCampanhaRequestDTO dto) {
        if (dto.diasExecucao().stream().anyMatch(dia -> dia == null || dia < 1 || dia > 7)) {
            erro("Os dias de execução devem estar entre 1 (segunda) e 7 (domingo)");
        }
        if (dto.etapas().stream().noneMatch(etapa -> etapa.tipo() == PipelineCadenciaTipo.TAREFA)) {
            erro("A cadência deve possuir ao menos uma tarefa");
        }
        PipelineCadenciaTipo ultimoTipo = dto.etapas().getLast().tipo();
        if (ultimoTipo != PipelineCadenciaTipo.ENCERRAR && ultimoTipo != PipelineCadenciaTipo.SEM_RESPOSTA) {
            erro("A última etapa deve encerrar o fluxo ou marcar como sem resposta");
        }
        dto.etapas().forEach(this::validarEtapa);
    }

    private void validarEtapa(PipelineCadenciaEtapaRequestDTO etapa) {
        if (etapa.tipo() == PipelineCadenciaTipo.TAREFA) {
            if (vazio(etapa.titulo()) || vazio(etapa.tipoTarefa()) || etapa.prioridade() == null) {
                erro("Etapas de tarefa exigem título, tipo e prioridade");
            }
            return;
        }
        if (etapa.tipo() == PipelineCadenciaTipo.ESPERA
                && (etapa.diasUteisEspera() == null || etapa.diasUteisEspera() < 1)) {
            erro("Etapas de espera exigem ao menos um dia útil");
        }
    }

    private boolean vazio(String valor) { return valor == null || valor.isBlank(); }

    private void erro(String mensagem) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
