package com.climb.api.model;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contratos")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrato")
    private Long idContrato;

    @OneToOne
    @JoinColumn(name = "proposta_id", unique = true)
    private Proposta proposta;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_responsavel")
    private Usuario responsavel;

    @ManyToMany
    @JoinTable(
            name = "contrato_participantes",
            joinColumns = @JoinColumn(name = "contrato_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id")
    )
    private Set<Usuario> participantes = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "empresa_nome_fantasia", nullable = false)
    private String empresaNomeFantasia;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "url_pdf")
    private String urlPdf;

    @Column(nullable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "servico", length = 50)
    private ServicoComercial servico;

    @Column(name = "data_aprovacao")
    private LocalDate dataAprovacao;

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero ASC")
    private java.util.List<ContratoParcela> parcelas = new java.util.ArrayList<>();

    public Long getIdContrato() { return idContrato; }
    public void setIdContrato(Long idContrato) { this.idContrato = idContrato; }

    public Proposta getProposta() { return proposta; }
    public void setProposta(Proposta proposta) { this.proposta = proposta; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Usuario getResponsavel() { return responsavel; }
    public void setResponsavel(Usuario responsavel) { this.responsavel = responsavel; }

    public Set<Usuario> getParticipantes() { return participantes; }
    public void setParticipantes(Set<Usuario> participantes) { this.participantes = participantes; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }

    public String getEmpresaNomeFantasia() { return empresaNomeFantasia; }
    public void setEmpresaNomeFantasia(String empresaNomeFantasia) { this.empresaNomeFantasia = empresaNomeFantasia; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public String getUrlPdf() { return urlPdf; }
    public void setUrlPdf(String urlPdf) { this.urlPdf = urlPdf; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public ServicoComercial getServico() { return servico; }
    public void setServico(ServicoComercial servico) { this.servico = servico; }
    public LocalDate getDataAprovacao() { return dataAprovacao; }
    public void setDataAprovacao(LocalDate dataAprovacao) { this.dataAprovacao = dataAprovacao; }
    public java.util.List<ContratoParcela> getParcelas() { return parcelas; }
    public void setParcelas(java.util.List<ContratoParcela> parcelas) {
        this.parcelas.clear();
        if (parcelas == null) return;
        parcelas.forEach(parcela -> {
            parcela.setContrato(this);
            this.parcelas.add(parcela);
        });
    }
}
