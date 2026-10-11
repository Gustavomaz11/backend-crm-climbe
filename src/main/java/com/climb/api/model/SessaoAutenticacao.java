package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @Entity @Table(name = "sessoes_autenticacao")
public class SessaoAutenticacao {
    @Id @Column(name = "id_sessao", length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_usuario") private Usuario usuario;
    @Column(name = "versao_autenticacao", nullable = false) private long versaoAutenticacao;
    @Column(name = "refresh_hash", nullable = false, length = 64) private String refreshHash;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
    @Column(name = "revogada_em") private Instant revogadaEm;
}
