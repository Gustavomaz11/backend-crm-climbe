package com.climb.api.model;

import com.climb.api.model.enums.PipelineVendasResultado;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipeline_vendas_etapas")
public class PipelineVendasEtapa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_etapa")
    private Long idEtapa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funil_id", nullable = false)
    private PipelineVendasFunil funil;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String objetivo;

    @Column(name = "criterios_conclusao", columnDefinition = "TEXT")
    private String criteriosConclusao;

    @Column(name = "tempo_maximo_permanencia_dias")
    private Integer tempoMaximoPermanenciaDias;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campos_obrigatorios", columnDefinition = "json")
    private List<String> camposObrigatorios = new ArrayList<>();

    @Column(nullable = false)
    private Integer posicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineVendasResultado resultado;

    @Column(nullable = false)
    private Boolean ativo = true;

    public Long getIdEtapa() { return idEtapa; }
    public void setIdEtapa(Long idEtapa) { this.idEtapa = idEtapa; }
    public PipelineVendasFunil getFunil() { return funil; }
    public void setFunil(PipelineVendasFunil funil) { this.funil = funil; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getObjetivo() { return objetivo; }
    public void setObjetivo(String objetivo) { this.objetivo = objetivo; }
    public String getCriteriosConclusao() { return criteriosConclusao; }
    public void setCriteriosConclusao(String criteriosConclusao) { this.criteriosConclusao = criteriosConclusao; }
    public Integer getTempoMaximoPermanenciaDias() { return tempoMaximoPermanenciaDias; }
    public void setTempoMaximoPermanenciaDias(Integer tempoMaximoPermanenciaDias) { this.tempoMaximoPermanenciaDias = tempoMaximoPermanenciaDias; }
    public List<String> getCamposObrigatorios() { return camposObrigatorios; }
    public void setCamposObrigatorios(List<String> camposObrigatorios) { this.camposObrigatorios = camposObrigatorios; }
    public Integer getPosicao() { return posicao; }
    public void setPosicao(Integer posicao) { this.posicao = posicao; }
    public PipelineVendasResultado getResultado() { return resultado; }
    public void setResultado(PipelineVendasResultado resultado) { this.resultado = resultado; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
}
