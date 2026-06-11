package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "relatorios")
public class Relatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_relatorio")
    private Long idRelatorio;

    @ManyToOne
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(name = "url_pdf")
    private String urlPdf;

    @Column(name = "data_envio")
    private LocalDate dataEnvio;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    public Long getIdRelatorio() { return idRelatorio; }
    public void setIdRelatorio(Long idRelatorio) { this.idRelatorio = idRelatorio; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public String getUrlPdf() { return urlPdf; }
    public void setUrlPdf(String urlPdf) { this.urlPdf = urlPdf; }

    public LocalDate getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDate dataEnvio) { this.dataEnvio = dataEnvio; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
}
