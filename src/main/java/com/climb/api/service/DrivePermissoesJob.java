package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.repository.EmpresaDriveServicoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class DrivePermissoesJob {
    private static final Logger log = LoggerFactory.getLogger(DrivePermissoesJob.class);
    private final EmpresaDriveServicoRepository pastas;
    private final DrivePermissoesService permissoes;
    private final GoogleDriveProperties properties;
    public DrivePermissoesJob(EmpresaDriveServicoRepository pastas, DrivePermissoesService permissoes, GoogleDriveProperties properties) {
        this.pastas = pastas; this.permissoes = permissoes; this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${google.drive.permissions-interval-ms:5000}", initialDelayString = "${google.drive.initial-delay-ms:5000}", scheduler = "googleDriveScheduler")
    public void processar() {
        if (!properties.isEnabled()) return;
        for (var pasta : pastas.findTop10ByPastaIdIsNotNullAndPermissoesTentarEmLessThanEqualOrderByPermissoesTentarEmAsc(LocalDateTime.now())) {
            try { permissoes.sincronizar(pasta.getId()); }
            catch (RuntimeException exception) { log.warn("Permissões Drive pendentes para o vínculo {} ({})", pasta.getId(), exception.getClass().getSimpleName()); }
        }
    }
}
