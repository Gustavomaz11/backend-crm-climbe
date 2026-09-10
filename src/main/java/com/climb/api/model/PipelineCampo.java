package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "pipeline_campos") @Getter @Setter
public class PipelineCampo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String nome;
    @Column(nullable = false, length = 20)
    private String tipo;
    @Column(nullable = false, length = 20)
    private String escopo;
    @Column(columnDefinition = "TEXT")
    private String opcoes;
    @Column(nullable = false)
    private Boolean ativo = true;
}
