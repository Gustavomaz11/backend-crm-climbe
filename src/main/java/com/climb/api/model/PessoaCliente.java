package com.climb.api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "pessoas_clientes")
public class PessoaCliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pessoa")
    private Long idPessoa;

    @Column(nullable = false, length = 180)
    private String nome;

    @Column(length = 14, unique = true)
    private String cpf;

    @Column(length = 180)
    private String email;

    @Column(length = 50)
    private String telefone;

    @Column(length = 120)
    private String cargo;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pessoa_cliente_empresas",
            joinColumns = @JoinColumn(name = "pessoa_id"),
            inverseJoinColumns = @JoinColumn(name = "empresa_id")
    )
    private Set<Empresa> empresas = new LinkedHashSet<>();

    public Long getIdPessoa() { return idPessoa; }
    public void setIdPessoa(Long idPessoa) { this.idPessoa = idPessoa; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public Set<Empresa> getEmpresas() { return empresas; }
    public void setEmpresas(Set<Empresa> empresas) { this.empresas = empresas == null ? new LinkedHashSet<>() : empresas; }
}
