package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "emails_notificacao_pendentes")
public class EmailNotificacaoPendente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 320)
    private String destinatario;
    @Column(nullable = false)
    private String assunto;
    @Column(columnDefinition = "TEXT")
    private String mensagem;
    @Column(length = 1000)
    private String destino;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
    @Column(name = "proxima_tentativa", nullable = false)
    private LocalDateTime proximaTentativa;
    @Column(nullable = false)
    private int tentativas;

    public EmailNotificacaoPendente() {}

    public EmailNotificacaoPendente(String destinatario, String assunto, String mensagem, String destino, LocalDateTime agora) {
        this.destinatario = destinatario; this.assunto = assunto;
        this.mensagem = mensagem; this.destino = destino;
        criadoEm = agora; proximaTentativa = agora;
    }
}
