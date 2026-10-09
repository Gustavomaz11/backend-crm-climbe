package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "contrato_rateio_tecnico_participantes", uniqueConstraints = @UniqueConstraint(
        name = "uk_rateio_tecnico_participante", columnNames = {"contrato_id", "usuario_id", "competencia"}))
public class ContratoRateioTecnicoParticipante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false)
    private LocalDate competencia;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDate getCompetencia() { return competencia; }
    public void setCompetencia(LocalDate competencia) { this.competencia = competencia; }
}
