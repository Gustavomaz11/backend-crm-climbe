package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "contratos")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrato")
    private Long idContrato;

    @OneToOne
    @JoinColumn(name = "proposta_id", nullable = false, unique = true)
    private Proposta proposta;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

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

    public Long getIdContrato() { return idContrato; }
    public void setIdContrato(Long idContrato) { this.idContrato = idContrato; }

    public Proposta getProposta() { return proposta; }
    public void setProposta(Proposta proposta) { this.proposta = proposta; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

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
}
