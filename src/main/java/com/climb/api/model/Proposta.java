package com.climb.api.model;

import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.enums.ServicoComercial;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "propostas")
public class Proposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proposta")
    private Long idProposta;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropostaStatus status;

    @Column(name = "url")
    private String url;

    @Column(name = "valuation", precision = 15, scale = 2)
    private BigDecimal valuation;

    @Enumerated(EnumType.STRING)
    @Column(name = "servico", length = 50)
    private ServicoComercial servico;

    @Column(name = "mes_inicio")
    private LocalDate mesInicio;

    @Column(name = "recorrencia_meses")
    private Integer recorrenciaMeses;

    @Column(name = "quantidade_parcelas", nullable = false)
    private Integer quantidadeParcelas = 1;

    @Column(name = "parcelas_iguais", nullable = false)
    private Boolean parcelasIguais = true;

    @Column(name = "comissao_tecnico_percentual", precision = 5, scale = 2)
    private BigDecimal comissaoTecnicoPercentual;

    @Column(name = "comissao_comercial_percentual", precision = 5, scale = 2)
    private BigDecimal comissaoComercialPercentual;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "proposta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("mesVigencia ASC")
    private List<PropostaReajuste> reajustes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposta_equipe_tecnica", joinColumns = @JoinColumn(name = "proposta_id"))
    @Column(name = "usuario_id")
    private Set<Long> equipeTecnicaIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "proposta_equipe_comercial", joinColumns = @JoinColumn(name = "proposta_id"))
    @Column(name = "usuario_id")
    private Set<Long> equipeComercialIds = new HashSet<>();

    @Column(name = "data_criacao")
    private LocalDate dataCriacao;

    public Long getIdProposta() { return idProposta; }
    public void setIdProposta(Long idProposta) { this.idProposta = idProposta; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public PropostaStatus getStatus() { return status; }
    public void setStatus(PropostaStatus status) { this.status = status; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public BigDecimal getValuation() { return valuation; }
    public void setValuation(BigDecimal valuation) { this.valuation = valuation; }

    public ServicoComercial getServico() { return servico; }
    public void setServico(ServicoComercial servico) { this.servico = servico; }
    public LocalDate getMesInicio() { return mesInicio; }
    public void setMesInicio(LocalDate mesInicio) { this.mesInicio = mesInicio; }
    public Integer getRecorrenciaMeses() { return recorrenciaMeses; }
    public void setRecorrenciaMeses(Integer recorrenciaMeses) { this.recorrenciaMeses = recorrenciaMeses; }
    public Integer getQuantidadeParcelas() { return quantidadeParcelas; }
    public void setQuantidadeParcelas(Integer quantidadeParcelas) { this.quantidadeParcelas = quantidadeParcelas; }
    public Boolean getParcelasIguais() { return parcelasIguais; }
    public void setParcelasIguais(Boolean parcelasIguais) { this.parcelasIguais = parcelasIguais; }
    public BigDecimal getComissaoTecnicoPercentual() { return comissaoTecnicoPercentual; }
    public void setComissaoTecnicoPercentual(BigDecimal valor) { this.comissaoTecnicoPercentual = valor; }
    public BigDecimal getComissaoComercialPercentual() { return comissaoComercialPercentual; }
    public void setComissaoComercialPercentual(BigDecimal valor) { this.comissaoComercialPercentual = valor; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public List<PropostaReajuste> getReajustes() { return reajustes; }
    public void setReajustes(List<PropostaReajuste> reajustes) {
        this.reajustes.clear();
        if (reajustes == null) return;
        reajustes.forEach(reajuste -> {
            reajuste.setProposta(this);
            this.reajustes.add(reajuste);
        });
    }
    public Set<Long> getEquipeTecnicaIds() { return equipeTecnicaIds; }
    public void setEquipeTecnicaIds(Set<Long> ids) { this.equipeTecnicaIds = ids == null ? new HashSet<>() : ids; }
    public Set<Long> getEquipeComercialIds() { return equipeComercialIds; }
    public void setEquipeComercialIds(Set<Long> ids) { this.equipeComercialIds = ids == null ? new HashSet<>() : ids; }

    public LocalDate getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDate dataCriacao) { this.dataCriacao = dataCriacao; }
}
