package com.climb.api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "pipeline_campanhas")
public class PipelineCampanha {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_campanha")
    private Long idCampanha;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false, length = 180)
    private String estrategia;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false)
    private Boolean ativo = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @ManyToMany
    @JoinTable(name = "pipeline_campanha_leads",
            joinColumns = @JoinColumn(name = "campanha_id"),
            inverseJoinColumns = @JoinColumn(name = "negocio_id"))
    private Set<PipelineVendasNegocio> leads = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "pipeline_campanha_participantes",
            joinColumns = @JoinColumn(name = "campanha_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private Set<Usuario> participantes = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "pipeline_campanha_scripts",
            joinColumns = @JoinColumn(name = "campanha_id"),
            inverseJoinColumns = @JoinColumn(name = "script_id"))
    private Set<PipelineScript> scripts = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "pipeline_campanha_dias_execucao", joinColumns = @JoinColumn(name = "campanha_id"))
    @Column(name = "dia_semana")
    private Set<Integer> diasExecucao = new LinkedHashSet<>();

    @OneToMany(mappedBy = "campanha", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<PipelineCadenciaEtapa> etapas = new ArrayList<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() { criadoEm = atualizadoEm = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { atualizadoEm = LocalDateTime.now(); }

    public void substituirEtapas(List<PipelineCadenciaEtapa> novas) {
        etapas.clear();
        novas.forEach(etapa -> { etapa.setCampanha(this); etapas.add(etapa); });
    }

    public Long getIdCampanha() { return idCampanha; }
    public void setIdCampanha(Long idCampanha) { this.idCampanha = idCampanha; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEstrategia() { return estrategia; }
    public void setEstrategia(String estrategia) { this.estrategia = estrategia; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public Set<PipelineVendasNegocio> getLeads() { return leads; }
    public void setLeads(Set<PipelineVendasNegocio> leads) { this.leads = leads; }
    public Set<Usuario> getParticipantes() { return participantes; }
    public void setParticipantes(Set<Usuario> participantes) { this.participantes = participantes; }
    public Set<PipelineScript> getScripts() { return scripts; }
    public void setScripts(Set<PipelineScript> scripts) { this.scripts = scripts; }
    public Set<Integer> getDiasExecucao() { return diasExecucao; }
    public void setDiasExecucao(Set<Integer> diasExecucao) { this.diasExecucao = diasExecucao; }
    public List<PipelineCadenciaEtapa> getEtapas() { return etapas; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }

    @Column(nullable = false)
    private Integer versao = 1;
    public Integer getVersao() { return versao; }
    public void setVersao(Integer value) { this.versao = value; }

}
