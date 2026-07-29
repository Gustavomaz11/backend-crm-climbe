package com.climb.api.model;

import com.climb.api.model.enums.PipelineExecucaoStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_campanha_execucoes")
public class PipelineCampanhaExecucao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_execucao")
    private Long idExecucao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private PipelineCampanha campanha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private PipelineVendasNegocio negocio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participante_id", nullable = false)
    private Usuario participante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PipelineExecucaoStatus status;

    @Column(name = "ordem_atual", nullable = false)
    private Integer ordemAtual = 0;

    @Column(name = "proxima_execucao_em")
    private LocalDateTime proximaExecucaoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarefa_atual_id")
    private PipelineVendasTarefa tarefaAtual;

    @Column(name = "iniciado_em", nullable = false)
    private LocalDateTime iniciadoEm;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (iniciadoEm == null) iniciadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() { atualizadoEm = LocalDateTime.now(); }

    public Long getIdExecucao() { return idExecucao; }
    public PipelineCampanha getCampanha() { return campanha; }
    public void setCampanha(PipelineCampanha campanha) { this.campanha = campanha; }
    public PipelineVendasNegocio getNegocio() { return negocio; }
    public void setNegocio(PipelineVendasNegocio negocio) { this.negocio = negocio; }
    public Usuario getParticipante() { return participante; }
    public void setParticipante(Usuario participante) { this.participante = participante; }
    public PipelineExecucaoStatus getStatus() { return status; }
    public void setStatus(PipelineExecucaoStatus status) { this.status = status; }
    public Integer getOrdemAtual() { return ordemAtual; }
    public void setOrdemAtual(Integer ordemAtual) { this.ordemAtual = ordemAtual; }
    public LocalDateTime getProximaExecucaoEm() { return proximaExecucaoEm; }
    public void setProximaExecucaoEm(LocalDateTime proximaExecucaoEm) { this.proximaExecucaoEm = proximaExecucaoEm; }
    public PipelineVendasTarefa getTarefaAtual() { return tarefaAtual; }
    public void setTarefaAtual(PipelineVendasTarefa tarefaAtual) { this.tarefaAtual = tarefaAtual; }
    public LocalDateTime getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(LocalDateTime iniciadoEm) { this.iniciadoEm = iniciadoEm; }
    public LocalDateTime getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(LocalDateTime finalizadoEm) { this.finalizadoEm = finalizadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
