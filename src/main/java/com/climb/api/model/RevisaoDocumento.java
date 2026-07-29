package com.climb.api.model;

import com.climb.api.model.enums.RevisaoDocumentoStatus;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "revisoes_documento")
public class RevisaoDocumento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RevisaoDocumentoTipo tipo;

    @Column(name = "referencia_id", nullable = false)
    private Long referenciaId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "empresa_nome", nullable = false)
    private String empresaNome;

    @Column(name = "destinatario_email", nullable = false)
    private String destinatarioEmail;

    @Column(name = "destinatario_nome")
    private String destinatarioNome;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "token_expira_em", nullable = false)
    private LocalDateTime tokenExpiraEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RevisaoDocumentoStatus status;

    @Column(name = "versao_atual", nullable = false)
    private int versaoAtual;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;

    @Column(name = "email_status", nullable = false, length = 20)
    private String emailStatus;

    @Column(name = "email_enviado_em")
    private LocalDateTime emailEnviadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RevisaoDocumentoTipo getTipo() { return tipo; }
    public void setTipo(RevisaoDocumentoTipo tipo) { this.tipo = tipo; }
    public Long getReferenciaId() { return referenciaId; }
    public void setReferenciaId(Long referenciaId) { this.referenciaId = referenciaId; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getEmpresaNome() { return empresaNome; }
    public void setEmpresaNome(String empresaNome) { this.empresaNome = empresaNome; }
    public String getDestinatarioEmail() { return destinatarioEmail; }
    public void setDestinatarioEmail(String destinatarioEmail) { this.destinatarioEmail = destinatarioEmail; }
    public String getDestinatarioNome() { return destinatarioNome; }
    public void setDestinatarioNome(String destinatarioNome) { this.destinatarioNome = destinatarioNome; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public LocalDateTime getTokenExpiraEm() { return tokenExpiraEm; }
    public void setTokenExpiraEm(LocalDateTime tokenExpiraEm) { this.tokenExpiraEm = tokenExpiraEm; }
    public RevisaoDocumentoStatus getStatus() { return status; }
    public void setStatus(RevisaoDocumentoStatus status) { this.status = status; }
    public int getVersaoAtual() { return versaoAtual; }
    public void setVersaoAtual(int versaoAtual) { this.versaoAtual = versaoAtual; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public LocalDateTime getRespondidoEm() { return respondidoEm; }
    public void setRespondidoEm(LocalDateTime respondidoEm) { this.respondidoEm = respondidoEm; }
    public String getEmailStatus() { return emailStatus; }
    public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }
    public LocalDateTime getEmailEnviadoEm() { return emailEnviadoEm; }
    public void setEmailEnviadoEm(LocalDateTime emailEnviadoEm) { this.emailEnviadoEm = emailEnviadoEm; }
}
