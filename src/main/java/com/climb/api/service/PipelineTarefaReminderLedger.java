package com.climb.api.service;

import com.climb.api.model.PipelineTarefaNotificacao;
import com.climb.api.repository.PipelineTarefaNotificacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PipelineTarefaReminderLedger {

    private final PipelineTarefaNotificacaoRepository repository;
    private final ZoneId zoneId;

    public PipelineTarefaReminderLedger(
            PipelineTarefaNotificacaoRepository repository,
            @Value("${app.pipeline.task-reminders.zone:America/Sao_Paulo}") String zone
    ) {
        this.repository = repository;
        this.zoneId = ZoneId.of(zone);
    }

    public Set<String> buscarChaves(Collection<String> chaves) {
        if (chaves.isEmpty()) return Set.of();
        return new HashSet<>(repository.findChavesExistentes(chaves));
    }

    public List<PipelineTarefaReminderDelivery> filtrarPendentes(
            List<PipelineTarefaReminderDelivery> entregas
    ) {
        if (entregas.isEmpty()) return List.of();
        Set<String> existentes = buscarChaves(entregas.stream()
                .map(PipelineTarefaReminderDelivery::chave)
                .toList());
        return entregas.stream().filter(entrega -> !existentes.contains(entrega.chave())).toList();
    }

    public void registrar(PipelineTarefaReminderDelivery entrega) {
        repository.save(toEntity(entrega));
    }

    public void registrarTodas(List<PipelineTarefaReminderDelivery> entregas) {
        repository.saveAll(entregas.stream().map(this::toEntity).toList());
    }

    private PipelineTarefaNotificacao toEntity(PipelineTarefaReminderDelivery entrega) {
        PipelineTarefaNotificacao notificacao = new PipelineTarefaNotificacao();
        notificacao.setTarefa(entrega.tarefa());
        notificacao.setDestinatario(entrega.destinatario());
        notificacao.setTipo(entrega.tipo());
        notificacao.setReferencia(entrega.referencia());
        notificacao.setChave(entrega.chave());
        notificacao.setEnviadoEm(LocalDateTime.now(zoneId));
        return notificacao;
    }
}
