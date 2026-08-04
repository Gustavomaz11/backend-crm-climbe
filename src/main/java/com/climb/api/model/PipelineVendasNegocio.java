package com.climb.api.model;

import com.climb.api.model.enums.PipelineVendasResultado;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "pipeline_vendas_negocios")
public class PipelineVendasNegocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_negocio")
    private Long idNegocio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funil_id", nullable = false)
    private PipelineVendasFunil funil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(name = "nome_empresa", nullable = false, length = 180)
    private String nomeEmpresa;

    @Column(name = "nome_contato", nullable = false, length = 180)
    private String nomeContato;

    @Column(nullable = false, length = 50)
    private String telefone;

    @Column(nullable = false, length = 180)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etapa_id", nullable = false)
    private PipelineVendasEtapa etapa;

    @Column(name = "data_reuniao")
    private LocalDateTime dataReuniao;

    @Column(name = "origem_negocio", nullable = false, length = 120)
    private String origemNegocio;

    @Column(name = "estrategia_comercial", nullable = false, columnDefinition = "TEXT")
    private String estrategiaComercial;

    @Column(name = "servico_interesse", nullable = false, length = 180)
    private String servicoInteresse;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "pipeline_vendas_negocio_servicos",
            joinColumns = @JoinColumn(name = "negocio_id")
    )
    @Column(name = "servico", nullable = false, length = 180)
    @OrderColumn(name = "ordem")
    private List<String> servicosInteresse = new ArrayList<>();

    @Column(name = "valor_estimado_proposta", precision = 15, scale = 2)
    private BigDecimal valorEstimadoProposta;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineVendasResultado resultado = PipelineVendasResultado.ABERTO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_perda_id")
    private PipelineMotivoPerda motivoPerda;

    @Column(name = "observacao_perda", length = 500)
    private String observacaoPerda;

    @Column(name = "encerrado_em")
    private LocalDateTime encerradoEm;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", unique = true)
    private Contrato contrato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ultima_movimentacao_em", nullable = false)
    private LocalDateTime ultimaMovimentacaoEm;

    public Long getIdNegocio() { return idNegocio; }
    public void setIdNegocio(Long idNegocio) { this.idNegocio = idNegocio; }
    public PipelineVendasFunil getFunil() { return funil; }
    public void setFunil(PipelineVendasFunil funil) { this.funil = funil; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getNomeEmpresa() { return nomeEmpresa; }
    public void setNomeEmpresa(String nomeEmpresa) { this.nomeEmpresa = nomeEmpresa; }
    public String getNomeContato() { return nomeContato; }
    public void setNomeContato(String nomeContato) { this.nomeContato = nomeContato; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Usuario getResponsavel() { return responsavel; }
    public void setResponsavel(Usuario responsavel) { this.responsavel = responsavel; }
    public PipelineVendasEtapa getEtapa() { return etapa; }
    public void setEtapa(PipelineVendasEtapa etapa) { this.etapa = etapa; }
    public LocalDateTime getDataReuniao() { return dataReuniao; }
    public void setDataReuniao(LocalDateTime dataReuniao) { this.dataReuniao = dataReuniao; }
    public String getOrigemNegocio() { return origemNegocio; }
    public void setOrigemNegocio(String origemNegocio) { this.origemNegocio = origemNegocio; }
    public String getEstrategiaComercial() { return estrategiaComercial; }
    public void setEstrategiaComercial(String estrategiaComercial) { this.estrategiaComercial = estrategiaComercial; }
    public String getServicoInteresse() {
        return servicosInteresse.isEmpty() ? servicoInteresse : String.join(", ", servicosInteresse);
    }
    public void setServicoInteresse(String servicoInteresse) {
        setServicosInteresse(servicoInteresse == null ? List.of() : List.of(servicoInteresse));
    }
    public List<String> getServicosInteresse() { return List.copyOf(servicosInteresse); }
    public void setServicosInteresse(Collection<String> servicosInteresse) {
        this.servicosInteresse.clear();
        if (servicosInteresse != null) this.servicosInteresse.addAll(servicosInteresse);
        this.servicoInteresse = String.join(", ", this.servicosInteresse);
    }
    public BigDecimal getValorEstimadoProposta() { return valorEstimadoProposta; }
    public void setValorEstimadoProposta(BigDecimal valorEstimadoProposta) { this.valorEstimadoProposta = valorEstimadoProposta; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public PipelineVendasResultado getResultado() { return resultado; }
    public void setResultado(PipelineVendasResultado resultado) { this.resultado = resultado; }
    public PipelineMotivoPerda getMotivoPerda() { return motivoPerda; }
    public void setMotivoPerda(PipelineMotivoPerda motivoPerda) { this.motivoPerda = motivoPerda; }
    public String getObservacaoPerda() { return observacaoPerda; }
    public void setObservacaoPerda(String observacaoPerda) { this.observacaoPerda = observacaoPerda; }
    public LocalDateTime getEncerradoEm() { return encerradoEm; }
    public void setEncerradoEm(LocalDateTime encerradoEm) { this.encerradoEm = encerradoEm; }
    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getUltimaMovimentacaoEm() { return ultimaMovimentacaoEm; }
    public void setUltimaMovimentacaoEm(LocalDateTime ultimaMovimentacaoEm) { this.ultimaMovimentacaoEm = ultimaMovimentacaoEm; }
}
