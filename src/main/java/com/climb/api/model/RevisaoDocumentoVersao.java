package com.climb.api.model;

import com.climb.api.model.enums.RevisaoDocumentoStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "revisoes_documento_versoes")
public class RevisaoDocumentoVersao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "revisao_id", nullable = false)
    private RevisaoDocumento revisao;

    @Column(nullable = false)
    private int numero;

    @Column(name = "arquivo_url", nullable = false, length = 2048)
    private String arquivoUrl;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "total_paginas", nullable = false)
    private int totalPaginas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RevisaoDocumentoStatus resultado;

    @Column(name = "comentario_geral", columnDefinition = "TEXT")
    private String comentarioGeral;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @ManyToOne
    @JoinColumn(name = "criado_por")
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;

    @Column(name = "zapsign_documento_token", unique = true, length = 100)
    private String zapsignDocumentoToken;

    @Column(name = "zapsign_signatario_token", length = 100)
    private String zapsignSignatarioToken;

    @Column(name = "zapsign_status", length = 30)
    private String zapsignStatus;

    @Column(name = "zapsign_criado_em")
    private LocalDateTime zapsignCriadoEm;

    @Column(name = "zapsign_assinado_em")
    private LocalDateTime zapsignAssinadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RevisaoDocumento getRevisao() { return revisao; }
    public void setRevisao(RevisaoDocumento revisao) { this.revisao = revisao; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getArquivoUrl() { return arquivoUrl; }
    public void setArquivoUrl(String arquivoUrl) { this.arquivoUrl = arquivoUrl; }
    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public int getTotalPaginas() { return totalPaginas; }
    public void setTotalPaginas(int totalPaginas) { this.totalPaginas = totalPaginas; }
    public RevisaoDocumentoStatus getResultado() { return resultado; }
    public void setResultado(RevisaoDocumentoStatus resultado) { this.resultado = resultado; }
    public String getComentarioGeral() { return comentarioGeral; }
    public void setComentarioGeral(String comentarioGeral) { this.comentarioGeral = comentarioGeral; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getRespondidoEm() { return respondidoEm; }
    public void setRespondidoEm(LocalDateTime respondidoEm) { this.respondidoEm = respondidoEm; }
    public String getZapsignDocumentoToken() { return zapsignDocumentoToken; }
    public void setZapsignDocumentoToken(String zapsignDocumentoToken) { this.zapsignDocumentoToken = zapsignDocumentoToken; }
    public String getZapsignSignatarioToken() { return zapsignSignatarioToken; }
    public void setZapsignSignatarioToken(String zapsignSignatarioToken) { this.zapsignSignatarioToken = zapsignSignatarioToken; }
    public String getZapsignStatus() { return zapsignStatus; }
    public void setZapsignStatus(String zapsignStatus) { this.zapsignStatus = zapsignStatus; }
    public LocalDateTime getZapsignCriadoEm() { return zapsignCriadoEm; }
    public void setZapsignCriadoEm(LocalDateTime zapsignCriadoEm) { this.zapsignCriadoEm = zapsignCriadoEm; }
    public LocalDateTime getZapsignAssinadoEm() { return zapsignAssinadoEm; }
    public void setZapsignAssinadoEm(LocalDateTime zapsignAssinadoEm) { this.zapsignAssinadoEm = zapsignAssinadoEm; }
}
