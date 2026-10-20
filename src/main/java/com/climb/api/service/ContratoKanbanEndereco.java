package com.climb.api.service;

import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.ContratoServicos;

public final class ContratoKanbanEndereco {
    private ContratoKanbanEndereco() {}

    public static String daTarefa(ContratoKanbanTask tarefa) {
        var servico = ContratoServicos.daTarefa(tarefa);
        return "/contratos/kanban?contrato=" + tarefa.getContrato().getIdContrato() + "&tarefaId=" + tarefa.getIdTask()
                + (servico == null ? "" : "&servico=" + servico.name());
    }
}
