package com.climb.api.service;

import com.climb.api.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class KanbanArquivamentoAutomaticoService {
    private final ContratoKanbanTaskRepository tarefasContrato;
    private final PipelineVendasTarefaRepository tarefasPipeline;
    private final PipelineVendasNegocioRepository negocios;
    private final ContratoRepository contratos;
    private final ReuniaoRepository reunioes;

    public KanbanArquivamentoAutomaticoService(ContratoKanbanTaskRepository tarefasContrato,
            PipelineVendasTarefaRepository tarefasPipeline, PipelineVendasNegocioRepository negocios,
            ContratoRepository contratos, ReuniaoRepository reunioes) {
        this.tarefasContrato = tarefasContrato; this.tarefasPipeline = tarefasPipeline;
        this.negocios = negocios; this.contratos = contratos; this.reunioes = reunioes;
    }

    @Scheduled(cron = "${app.kanban.arquivamento.cron:0 */15 * * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void executar() {
        arquivarVencidos(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
    }

    @Transactional
    public int arquivarVencidos(LocalDateTime agora) {
        return tarefasContrato.arquivarVencidos(agora) + tarefasPipeline.arquivarVencidos(agora)
                + negocios.arquivarVencidos(agora) + contratos.arquivarVencidos(agora)
                + reunioes.arquivarVencidos(agora);
    }
}
