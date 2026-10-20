package com.climb.api.model;

import jakarta.persistence.*;

@Entity
@jakarta.persistence.EntityListeners(com.climb.api.model.listener.DrivePermissoesPersistenciaListener.class)
@Table(name = "contrato_equipe_servico", uniqueConstraints = @UniqueConstraint(
        name = "uk_contrato_equipe_servico", columnNames = {"contrato_id", "usuario_id", "servico"}))
public class ContratoEquipeMembro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false, length = 50)
    private String servico = "SEM_SERVICO";

    public String getServico() { return servico; }
    public void setServico(String servico) { this.servico = servico; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
