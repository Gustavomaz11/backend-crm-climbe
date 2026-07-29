package com.climb.api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_vendas_subtarefas")
public class PipelineVendasSubtarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_subtarefa")
    private Long idSubtarefa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tarefa_id", nullable = false)
    private PipelineVendasTarefa tarefa;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(nullable = false)
    private boolean concluida;

    @Column(nullable = false)
    private Integer posicao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() { atualizadoEm = LocalDateTime.now(); }

    public Long getIdSubtarefa() { return idSubtarefa; }
    public void setIdSubtarefa(Long idSubtarefa) { this.idSubtarefa = idSubtarefa; }
    public PipelineVendasTarefa getTarefa() { return tarefa; }
    public void setTarefa(PipelineVendasTarefa tarefa) { this.tarefa = tarefa; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public boolean isConcluida() { return concluida; }
    public void setConcluida(boolean concluida) { this.concluida = concluida; }
    public Integer getPosicao() { return posicao; }
    public void setPosicao(Integer posicao) { this.posicao = posicao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
