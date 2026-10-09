package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarefa_anexos")
@Getter @Setter
public class TarefaAnexo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contrato_task_id")
    private ContratoKanbanTask contratoTask;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pipeline_tarefa_id")
    private PipelineVendasTarefa pipelineTarefa;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "comentario_id")
    private TarefaComentario comentario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @Column(nullable = false, length = 255)
    private String nome;
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;
    @Column(nullable = false)
    private long tamanho;
    @Column(nullable = false, length = 1000)
    private String chave;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
    @PrePersist
    void prePersist() { if (criadoEm == null) criadoEm = LocalDateTime.now(); }
}
