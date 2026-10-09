package com.climb.api.model;

import com.climb.api.model.enums.PipelineTarefaPrioridade;
import com.climb.api.model.enums.PipelineTarefaStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "pipeline_vendas_tarefas")
public class PipelineVendasTarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarefa")
    private Long idTarefa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private PipelineVendasNegocio negocio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campanha_id")
    private PipelineCampanha campanha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etapa_cadencia_id")
    private PipelineCadenciaEtapa etapaCadencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    private PipelineScript script;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;

    @ManyToMany
    @JoinTable(name = "pipeline_tarefa_responsaveis", joinColumns = @JoinColumn(name = "tarefa_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private Set<Usuario> responsaveis = new LinkedHashSet<>();

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineTarefaPrioridade prioridade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PipelineTarefaStatus status;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "concluido_em")
    private LocalDateTime concluidoEm;

    @Column(name = "justificativa_atraso", length = 4000)
    private String justificativaAtraso;
    @Column(name = "justificada_em")
    private LocalDateTime justificadaEm;
    @Column(name = "justificada_por")
    private Long justificadaPor;

    public String getJustificativaAtraso() { return justificativaAtraso; }
    public void justificarAtraso(String texto, Long usuarioId) {
        justificativaAtraso = texto; justificadaPor = usuarioId;
        justificadaEm = LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
    }

    @OneToMany(mappedBy = "tarefa", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicao ASC, idSubtarefa ASC")
    private List<PipelineVendasSubtarefa> subtarefas = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public void substituirSubtarefas(List<PipelineVendasSubtarefa> novasSubtarefas) {
        subtarefas.clear();
        novasSubtarefas.forEach(subtarefa -> {
            subtarefa.setTarefa(this);
            subtarefas.add(subtarefa);
        });
    }

    public Long getIdTarefa() { return idTarefa; }
    public void setIdTarefa(Long idTarefa) { this.idTarefa = idTarefa; }
    public PipelineVendasNegocio getNegocio() { return negocio; }
    public void setNegocio(PipelineVendasNegocio negocio) { this.negocio = negocio; }
    public PipelineCampanha getCampanha() { return campanha; }
    public void setCampanha(PipelineCampanha campanha) { this.campanha = campanha; }
    public PipelineCadenciaEtapa getEtapaCadencia() { return etapaCadencia; }
    public void setEtapaCadencia(PipelineCadenciaEtapa etapaCadencia) { this.etapaCadencia = etapaCadencia; }
    public PipelineScript getScript() { return script; }
    public void setScript(PipelineScript script) { this.script = script; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
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
    public LocalDate getPrazo() { return prazo; }
    public void setPrazo(LocalDate prazo) { this.prazo = prazo; }
    public PipelineTarefaPrioridade getPrioridade() { return prioridade; }
    public void setPrioridade(PipelineTarefaPrioridade prioridade) { this.prioridade = prioridade; }
    public PipelineTarefaStatus getStatus() { return status; }
    public void setStatus(PipelineTarefaStatus status) { this.status = status; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public LocalDateTime getConcluidoEm() { return concluidoEm; }
    public void setConcluidoEm(LocalDateTime concluidoEm) { this.concluidoEm = concluidoEm; }
    public List<PipelineVendasSubtarefa> getSubtarefas() { return subtarefas; }

    @Column(name = "motivo_cancelamento", length = 80)
    private String motivoCancelamento;
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String value) { this.motivoCancelamento = value; }


    @Column(name = "comentario_cancelamento", length = 1000)
    private String comentarioCancelamento;
    public String getComentarioCancelamento() { return comentarioCancelamento; }
    public void setComentarioCancelamento(String value) { this.comentarioCancelamento = value; }


    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;
    public LocalDateTime getCanceladoEm() { return canceladoEm; }
    public void setCanceladoEm(LocalDateTime value) { this.canceladoEm = value; }


    @Column(name = "cancelado_por")
    private Long canceladoPor;
    public Long getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(Long value) { this.canceladoPor = value; }

}
