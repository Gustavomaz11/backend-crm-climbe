package com.climb.api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_vendas_movimentacoes_etapa")
public class PipelineVendasMovimentacaoEtapa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimentacao")
    private Long idMovimentacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private PipelineVendasNegocio negocio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etapa_id", nullable = false)
    private PipelineVendasEtapa etapa;

    @Column(name = "entrada_em", nullable = false)
    private LocalDateTime entradaEm;

    @Column(name = "saida_em")
    private LocalDateTime saidaEm;

    public Long getIdMovimentacao() { return idMovimentacao; }
    public PipelineVendasNegocio getNegocio() { return negocio; }
    public void setNegocio(PipelineVendasNegocio negocio) { this.negocio = negocio; }
    public PipelineVendasEtapa getEtapa() { return etapa; }
    public void setEtapa(PipelineVendasEtapa etapa) { this.etapa = etapa; }
    public LocalDateTime getEntradaEm() { return entradaEm; }
    public void setEntradaEm(LocalDateTime entradaEm) { this.entradaEm = entradaEm; }
    public LocalDateTime getSaidaEm() { return saidaEm; }
    public void setSaidaEm(LocalDateTime saidaEm) { this.saidaEm = saidaEm; }
}
