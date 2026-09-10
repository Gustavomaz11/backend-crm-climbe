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
        var grupos = dto.etapas().stream().collect(java.util.stream.Collectors.groupingBy(e -> e.etapaFunilCodigo() == null ? "" : e.etapaFunilCodigo()));
        if (grupos.containsKey("") && grupos.size() > 1) erro("Não misture cadência geral com fluxos por etapa");
        grupos.forEach((codigo, etapas) -> {
            if (!java.util.Set.of("", "LISTA_LEADS", "TENTATIVA_CONTATO", "LEAD_CONECTADO", "REUNIAO_MARCADA", "REUNIAO_REALIZADA").contains(codigo)) erro("Etapa de pré-vendas inválida");
            if (etapas.stream().noneMatch(e -> e.tipo() == PipelineCadenciaTipo.TAREFA)) erro("Cada fluxo precisa de uma tarefa");
            var ultimo = etapas.getLast().tipo();
            if (ultimo != PipelineCadenciaTipo.ENCERRAR && ultimo != PipelineCadenciaTipo.SEM_RESPOSTA) erro("Cada fluxo deve terminar com encerramento");
            if (etapas.subList(0, etapas.size() - 1).stream().anyMatch(e -> e.tipo() == PipelineCadenciaTipo.ENCERRAR || e.tipo() == PipelineCadenciaTipo.SEM_RESPOSTA)) erro("O encerramento deve ser o último passo");
        });
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
