package com.climb.api.model;

import com.climb.api.model.enums.PipelineHistoricoTipo;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_vendas_historico")
public class PipelineVendasHistorico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historico")
    private Long idHistorico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private PipelineVendasNegocio negocio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50)
    private PipelineHistoricoTipo tipoEvento;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void prePersist() { if (criadoEm == null) criadoEm = LocalDateTime.now(); }

    public Long getIdHistorico() { return idHistorico; }
    public PipelineVendasNegocio getNegocio() { return negocio; }
    public void setNegocio(PipelineVendasNegocio negocio) { this.negocio = negocio; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public PipelineHistoricoTipo getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(PipelineHistoricoTipo tipoEvento) { this.tipoEvento = tipoEvento; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
