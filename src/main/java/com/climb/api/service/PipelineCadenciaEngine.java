package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.PipelineCampanhaExecucaoRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

@Service
public class PipelineCadenciaEngine {
    private final PipelineCampanhaExecucaoRepository execucaoRepository;
    private final PipelineVendasTarefaRepository tarefaRepository;
    private final PipelineDiasUteis diasUteis;
    private final PipelineTemplateRenderer renderer;
    private final PipelineHistoricoService historicoService;

    public PipelineCadenciaEngine(PipelineCampanhaExecucaoRepository execucaoRepository,
                                  PipelineVendasTarefaRepository tarefaRepository,
                                  PipelineDiasUteis diasUteis,
                                  PipelineTemplateRenderer renderer,
                                  PipelineHistoricoService historicoService) {
        this.execucaoRepository = execucaoRepository;
        this.tarefaRepository = tarefaRepository;
        this.diasUteis = diasUteis;
        this.renderer = renderer;
        this.historicoService = historicoService;
    }

    @Scheduled(fixedDelayString = "${app.pipeline.cadencia.intervalo-ms:60000}",
            initialDelayString = "${app.pipeline.cadencia.atraso-inicial-ms:15000}")
    @Transactional
    public void processarPendentes() {
        LocalDateTime agora = LocalDateTime.now();
        execucaoRepository.buscarAtivasParaProcessamento().forEach(execucao -> processar(execucao, agora));
    }

    @Transactional
    public void tarefaConcluida(PipelineVendasTarefa tarefa) {
        execucaoRepository.findByTarefaAtualIdTarefa(tarefa.getIdTarefa())
                .filter(execucao -> execucao.getStatus() == PipelineExecucaoStatus.ATIVA)
                .ifPresent(execucao -> processar(execucao, LocalDateTime.now()));
    }

    private void processar(PipelineCampanhaExecucao execucao, LocalDateTime agora) {
        if (!Boolean.TRUE.equals(execucao.getCampanha().getAtivo())) {
            pausar(execucao);
            return;
        }
        if (execucao.getNegocio().getResultado() != PipelineVendasResultado.ABERTO) {
            finalizar(execucao, PipelineExecucaoStatus.CONCLUIDA, agora, "Cadência encerrada: negócio concluído");
            return;
        }
        if (!liberarTarefaConcluida(execucao, agora)) return;
        if (execucao.getProximaExecucaoEm() != null && execucao.getProximaExecucaoEm().isAfter(agora)) return;
        executarEtapa(execucao, agora);
    }

    private boolean liberarTarefaConcluida(PipelineCampanhaExecucao execucao, LocalDateTime agora) {
        if (execucao.getTarefaAtual() == null) return true;
        if (execucao.getTarefaAtual().getStatus() != PipelineTarefaStatus.CONCLUIDA) return false;
        execucao.setTarefaAtual(null);
        execucao.setOrdemAtual(execucao.getOrdemAtual() + 1);
        execucao.setProximaExecucaoEm(agora);
        return true;
    }

    private void executarEtapa(PipelineCampanhaExecucao execucao, LocalDateTime agora) {
        PipelineCadenciaEtapa etapa = etapaAtual(execucao);
        if (etapa == null) {
            finalizar(execucao, PipelineExecucaoStatus.CONCLUIDA, agora, "Cadência concluída");
            return;
        }
        switch (etapa.getTipo()) {
            case TAREFA -> criarTarefaOuAgendar(execucao, etapa, agora);
            case ESPERA -> aplicarEspera(execucao, etapa, agora);
            case ENCERRAR -> finalizar(execucao, PipelineExecucaoStatus.CONCLUIDA, agora, "Cadência concluída");
            case SEM_RESPOSTA -> finalizar(execucao, PipelineExecucaoStatus.SEM_RESPOSTA, agora,
                    "Cadência encerrada sem resposta");
        }
    }

    private void criarTarefaOuAgendar(PipelineCampanhaExecucao execucao,
                                      PipelineCadenciaEtapa etapa,
                                      LocalDateTime agora) {
        LocalDateTime permitido = diasUteis.proximoPermitido(agora, execucao.getCampanha().getDiasExecucao());
        if (permitido.isAfter(agora)) {
            execucao.setProximaExecucaoEm(permitido);
            execucaoRepository.save(execucao);
            return;
        }
        PipelineVendasTarefa tarefa = novaTarefa(execucao, etapa, agora.toLocalDate());
        tarefaRepository.save(tarefa);
        execucao.setTarefaAtual(tarefa);
        execucao.setProximaExecucaoEm(null);
        execucaoRepository.save(execucao);
        historicoService.registrar(execucao.getNegocio(), execucao.getCampanha().getCriadoPor().getId(),
                PipelineHistoricoTipo.CADENCIA_TAREFA_CRIADA,
                "Tarefa automática criada pela campanha " + execucao.getCampanha().getNome() + ": " + tarefa.getTitulo());
    }

    private PipelineVendasTarefa novaTarefa(PipelineCampanhaExecucao execucao,
                                             PipelineCadenciaEtapa etapa,
                                             LocalDate hoje) {
        PipelineVendasTarefa tarefa = new PipelineVendasTarefa();
        tarefa.setNegocio(execucao.getNegocio());
        tarefa.setCampanha(execucao.getCampanha());
        tarefa.setEtapaCadencia(etapa);
        tarefa.setScript(etapa.getScript());
        tarefa.setTitulo(renderer.renderizar(etapa.getTitulo(), execucao.getNegocio(), execucao.getParticipante()));
        tarefa.setDescricao(descricaoRenderizada(etapa, execucao));
        tarefa.setResponsavel(execucao.getParticipante());
        tarefa.setDataInicio(hoje);
        tarefa.setPrazo(diasUteis.adicionar(hoje, etapa.getPrazoDiasUteis() == null ? 0 : etapa.getPrazoDiasUteis()));
        tarefa.setPrioridade(etapa.getPrioridade());
        tarefa.setStatus(PipelineTarefaStatus.PENDENTE);
        tarefa.setTipo(etapa.getTipoTarefa());
        tarefa.setObservacoes("Gerada automaticamente pela campanha " + execucao.getCampanha().getNome());
        tarefa.setCriadoPor(execucao.getCampanha().getCriadoPor());
        return tarefa;
    }

    private String descricaoRenderizada(PipelineCadenciaEtapa etapa, PipelineCampanhaExecucao execucao) {
        String descricao = renderer.renderizar(etapa.getDescricao(), execucao.getNegocio(), execucao.getParticipante());
        String script = etapa.getScript() == null ? null : renderer.renderizar(
                etapa.getScript().getModeloMensagem(), execucao.getNegocio(), execucao.getParticipante());
        if (descricao == null) return script;
        if (script == null) return descricao;
        return descricao + "\n\nScript sugerido:\n" + script;
    }

    private void aplicarEspera(PipelineCampanhaExecucao execucao,
                               PipelineCadenciaEtapa etapa,
                               LocalDateTime agora) {
        execucao.setOrdemAtual(execucao.getOrdemAtual() + 1);
        execucao.setProximaExecucaoEm(diasUteis.aposEspera(
                agora, etapa.getDiasUteisEspera(), execucao.getCampanha().getDiasExecucao()));
        execucaoRepository.save(execucao);
    }

    private PipelineCadenciaEtapa etapaAtual(PipelineCampanhaExecucao execucao) {
        return execucao.getCampanha().getEtapas().stream()
                .filter(etapa -> etapa.getOrdem().equals(execucao.getOrdemAtual()))
                .min(Comparator.comparing(PipelineCadenciaEtapa::getOrdem)).orElse(null);
    }

    private void pausar(PipelineCampanhaExecucao execucao) {
        execucao.setStatus(PipelineExecucaoStatus.PAUSADA);
        execucao.setProximaExecucaoEm(null);
        execucaoRepository.save(execucao);
    }

    private void finalizar(PipelineCampanhaExecucao execucao,
                           PipelineExecucaoStatus status,
                           LocalDateTime agora,
                           String descricao) {
        execucao.setStatus(status);
        execucao.setFinalizadoEm(agora);
        execucao.setProximaExecucaoEm(null);
        execucao.setTarefaAtual(null);
        execucaoRepository.save(execucao);
        historicoService.registrar(execucao.getNegocio(), execucao.getCampanha().getCriadoPor().getId(),
                PipelineHistoricoTipo.CADENCIA_CONCLUIDA, descricao + " — " + execucao.getCampanha().getNome());
    }
}
