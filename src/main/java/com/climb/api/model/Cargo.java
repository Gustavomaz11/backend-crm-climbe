package com.climb.api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cargos")
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "cargo_superior_id")
    private Long cargoSuperiorId;

    @Column(name = "ordem_hierarquia", nullable = false)
    private int ordemHierarquia;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public Long getCargoSuperiorId() { return cargoSuperiorId; }
    public void setCargoSuperiorId(Long cargoSuperiorId) { this.cargoSuperiorId = cargoSuperiorId; }

    public int getOrdemHierarquia() { return ordemHierarquia; }
    public void setOrdemHierarquia(int ordemHierarquia) { this.ordemHierarquia = ordemHierarquia; }
}
