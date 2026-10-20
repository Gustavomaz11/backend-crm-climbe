package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Embeddable
public class ContratoServicoEncerramento {
    @Column(name = "encerrado_em", nullable = false)
    private LocalDateTime encerradoEm;

    @ManyToOne
    @JoinColumn(name = "encerrado_por", nullable = false)
    private Usuario encerradoPor;

    protected ContratoServicoEncerramento() {}
    public ContratoServicoEncerramento(LocalDateTime encerradoEm, Usuario encerradoPor) {
        this.encerradoEm = encerradoEm; this.encerradoPor = encerradoPor;
    }
    public LocalDateTime getEncerradoEm() { return encerradoEm; }
    public Usuario getEncerradoPor() { return encerradoPor; }
}
