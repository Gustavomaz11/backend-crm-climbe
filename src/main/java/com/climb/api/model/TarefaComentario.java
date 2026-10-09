package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarefa_comentarios")
@Getter @Setter
public class TarefaComentario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contrato_task_id")
    private ContratoKanbanTask contratoTask;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pipeline_tarefa_id")
    private PipelineVendasTarefa pipelineTarefa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "comentario_pai_id")
    private TarefaComentario comentarioPai;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
    @PrePersist
    void prePersist() { if (criadoEm == null) criadoEm = LocalDateTime.now(); }
}
