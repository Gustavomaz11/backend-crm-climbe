package com.climb.api.service;

import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.PipelineTarefaNotificacaoTipo;

record PipelineTarefaReminderDelivery(
        PipelineVendasTarefa tarefa,
        Usuario destinatario,
        PipelineTarefaNotificacaoTipo tipo,
        String referencia,
        String chave
) {}
