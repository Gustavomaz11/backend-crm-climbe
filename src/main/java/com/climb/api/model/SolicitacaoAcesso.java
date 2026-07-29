package com.climb.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "solicitacoes_acesso",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_solicitacoes_acesso_origem_referencia",
                columnNames = {"origem", "referencia_id"}))
public class SolicitacaoAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SolicitacaoAcessoOrigem origem;

    @Column(name = "referencia_id", nullable = false)
    private Long referenciaId;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(nullable = false)
    private String email;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    private String cpf;
    private String contato;

    @Column(name = "cargo_nome")
    private String cargoNome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SolicitacaoAcessoStatus status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @Column(name = "decidido_em")
    private LocalDateTime decididoEm;

    @Column(name = "decidido_por")
    private Long decididoPor;
}
