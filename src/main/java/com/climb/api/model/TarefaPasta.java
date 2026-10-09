package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarefa_pastas")
@Getter @Setter
public class TarefaPasta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contrato_task_id")
    private ContratoKanbanTask contratoTask;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pipeline_tarefa_id")
    private PipelineVendasTarefa pipelineTarefa;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pasta_pai_id")
    private TarefaPasta pastaPai;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @Column(nullable = false, length = 120)
    private String nome;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
    @PrePersist
    void prePersist() { if (criadoEm == null) criadoEm = LocalDateTime.now(); }
}
