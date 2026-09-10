package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "pipeline_tags") @Getter @Setter
public class PipelineTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String nome;
    @Column(nullable = false, length = 7)
    private String cor;
    @Column(nullable = false)
    private Boolean ativo = true;
}
