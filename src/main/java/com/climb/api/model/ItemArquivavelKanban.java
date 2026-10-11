package com.climb.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@MappedSuperclass
public abstract class ItemArquivavelKanban {
    @Column(name = "arquivado_em")
    private LocalDateTime arquivadoEm;
    @Column(name = "arquivar_apos")
    private LocalDateTime arquivarApos;

    public LocalDateTime getArquivadoEm() { return arquivadoEm; }
    public LocalDateTime getArquivarApos() { return arquivarApos; }
    public boolean estaArquivado() { return arquivadoEm != null; }
    public abstract boolean podeArquivar();

    public void alterarArquivamento(boolean arquivar, LocalDateTime agora) {
        if (arquivar) {
            if (!podeArquivar()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Somente itens concluídos, ganhos ou perdidos podem ser arquivados.");
            if (arquivadoEm == null) arquivadoEm = agora;
            return;
        }
        if (arquivadoEm != null) {
            arquivadoEm = null;
            arquivarApos = podeArquivar() ? agora.plusDays(15) : null;
        }
    }

    public void sincronizarPrazoArquivamento(LocalDateTime agora) {
        if (!podeArquivar()) {
            arquivarApos = null;
            arquivadoEm = null;
        } else if (arquivarApos == null) {
            arquivarApos = agora.plusDays(15);
        }
    }

    @PrePersist
    @PreUpdate
    private void sincronizarArquivamento() {
        sincronizarPrazoArquivamento(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
    }
}
