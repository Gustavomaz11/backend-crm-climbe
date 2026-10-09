package com.climb.api.model;

import com.climb.api.model.enums.ContratoKanbanPrioridade;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "contrato_kanban_tasks")
public class ContratoKanbanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_task")
    private Long idTask;

    @ManyToOne
    @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;

    @ManyToOne
    @JoinColumn(name = "raia_id", nullable = false)
    private ContratoKanbanRaia raia;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContratoKanbanPrioridade prioridade = ContratoKanbanPrioridade.MEDIA;

    @ManyToOne
    @JoinColumn(name = "id_responsavel")
    private Usuario responsavel;

    @ManyToMany
    @JoinTable(name = "contrato_kanban_task_responsaveis", joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private Set<Usuario> responsaveis = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "contrato_tarefa_apoios", joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private Set<Usuario> apoios = new LinkedHashSet<>();

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(nullable = false)
    private Integer posicao = 0;

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
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public Long getIdTask() { return idTask; }
    public void setIdTask(Long idTask) { this.idTask = idTask; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public ContratoKanbanRaia getRaia() { return raia; }
    public void setRaia(ContratoKanbanRaia raia) { this.raia = raia; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public ContratoKanbanPrioridade getPrioridade() { return prioridade; }
    public void setPrioridade(ContratoKanbanPrioridade prioridade) { this.prioridade = prioridade; }

    public Usuario getResponsavel() { return responsavel; }
    public void setResponsavel(Usuario responsavel) { this.responsavel = responsavel; }
    public Set<Usuario> getResponsaveis() { return responsaveis; }
    public Set<Usuario> getResponsaveisEfetivos() {
        return responsaveis.isEmpty() && responsavel != null ? Set.of(responsavel) : responsaveis;
    }
    public void setResponsaveis(Collection<Usuario> usuarios) {
        responsaveis.clear();
        responsaveis.addAll(usuarios);
        responsavel = responsaveis.stream().findFirst().orElse(null);
    }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public Integer getPosicao() { return posicao; }
    public void setPosicao(Integer posicao) { this.posicao = posicao; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public Set<Usuario> getApoios() { return apoios; }
    public LocalDateTime getConcluidaEm() { return concluidaEm; }
    public void setConcluidaEm(LocalDateTime concluidaEm) { this.concluidaEm = concluidaEm; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
