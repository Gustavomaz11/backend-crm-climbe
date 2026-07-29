package com.climb.api.model;

import com.climb.api.model.enums.PipelineCadenciaTipo;
import com.climb.api.model.enums.PipelineTarefaPrioridade;
import jakarta.persistence.*;

@Entity
@Table(name = "pipeline_cadencia_etapas")
public class PipelineCadenciaEtapa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_etapa_cadencia")
    private Long idEtapaCadencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private PipelineCampanha campanha;

    @Column(nullable = false)
    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PipelineCadenciaTipo tipo;

    @Column(length = 180)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "tipo_tarefa", length = 100)
    private String tipoTarefa;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PipelineTarefaPrioridade prioridade;

    @Column(name = "dias_uteis_espera", nullable = false)
    private Integer diasUteisEspera = 0;

    @Column(name = "prazo_dias_uteis")
    private Integer prazoDiasUteis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id")
    private PipelineScript script;

    public Long getIdEtapaCadencia() { return idEtapaCadencia; }
    public void setIdEtapaCadencia(Long idEtapaCadencia) { this.idEtapaCadencia = idEtapaCadencia; }
    public PipelineCampanha getCampanha() { return campanha; }
    public void setCampanha(PipelineCampanha campanha) { this.campanha = campanha; }
    public Integer getOrdem() { return ordem; }
    public void setOrdem(Integer ordem) { this.ordem = ordem; }
    public PipelineCadenciaTipo getTipo() { return tipo; }
    public void setTipo(PipelineCadenciaTipo tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getTipoTarefa() { return tipoTarefa; }
    public void setTipoTarefa(String tipoTarefa) { this.tipoTarefa = tipoTarefa; }
    public PipelineTarefaPrioridade getPrioridade() { return prioridade; }
    public void setPrioridade(PipelineTarefaPrioridade prioridade) { this.prioridade = prioridade; }
    public Integer getDiasUteisEspera() { return diasUteisEspera; }
    public void setDiasUteisEspera(Integer diasUteisEspera) { this.diasUteisEspera = diasUteisEspera; }
    public Integer getPrazoDiasUteis() { return prazoDiasUteis; }
    public void setPrazoDiasUteis(Integer prazoDiasUteis) { this.prazoDiasUteis = prazoDiasUteis; }
    public PipelineScript getScript() { return script; }
    public void setScript(PipelineScript script) { this.script = script; }
}
