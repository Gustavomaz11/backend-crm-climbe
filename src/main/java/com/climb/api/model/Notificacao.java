package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacoes")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacao")
    private Long idNotificacao;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "mensagem")
    private String mensagem;

    @Column(name = "data_envio")
    private LocalDate dataEnvio;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "lida", nullable = false)
    private Boolean lida = false;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_leitura")
    private LocalDateTime dataLeitura;

    @PrePersist
    public void prePersist() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (dataEnvio == null) {
            dataEnvio = LocalDate.now();
        }
        if (lida == null) {
            lida = false;
        }
    }

    public Long getIdNotificacao() { return idNotificacao; }
    public void setIdNotificacao(Long idNotificacao) { this.idNotificacao = idNotificacao; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public LocalDate getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDate dataEnvio) { this.dataEnvio = dataEnvio; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Boolean getLida() { return lida; }
    public void setLida(Boolean lida) { this.lida = lida; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataLeitura() { return dataLeitura; }
    public void setDataLeitura(LocalDateTime dataLeitura) { this.dataLeitura = dataLeitura; }
}
