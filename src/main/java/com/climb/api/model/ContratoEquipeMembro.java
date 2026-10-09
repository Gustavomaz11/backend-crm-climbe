package com.climb.api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "contrato_equipe", uniqueConstraints = @UniqueConstraint(
        name = "uk_contrato_equipe", columnNames = {"contrato_id", "usuario_id"}))
public class ContratoEquipeMembro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
