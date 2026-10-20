package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.repository.EmpresaDriveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class DriveSincronizacaoJob {
    private static final Logger log = LoggerFactory.getLogger(DriveSincronizacaoJob.class);
    private final EmpresaDriveRepository fila;
    private final DriveSincronizacaoService sincronizacao;
    private final GoogleDriveProperties properties;

    public DriveSincronizacaoJob(EmpresaDriveRepository fila, DriveSincronizacaoService sincronizacao, GoogleDriveProperties properties) {
        this.fila = fila; this.sincronizacao = sincronizacao; this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${google.drive.interval-ms:5000}", initialDelayString = "${google.drive.initial-delay-ms:5000}", scheduler = "googleDriveScheduler")
    public void processar() {
        if (!properties.isEnabled()) return;
        for (var item : fila.findTop10ByPendenteTrueAndTentarEmLessThanEqualOrderByTentarEmAsc(LocalDateTime.now())) {
            try { sincronizacao.sincronizar(item.getEmpresaId()); }
            catch (RuntimeException exception) { log.warn("Sincronização Drive pendente para a empresa {} ({})", item.getEmpresaId(), exception.getClass().getSimpleName()); }
        }
    }
}
