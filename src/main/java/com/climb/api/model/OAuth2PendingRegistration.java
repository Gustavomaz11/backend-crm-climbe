package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "oauth2_pending_registrations")
public class OAuth2PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(nullable = false)
    private String email;

    @Column
    private String nome;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "token_unico", nullable = false)
    private String tokenUnico;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    private Boolean consumido = false;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private Boolean aprovado = false;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @Column(name = "aprovado_por")
    private Long aprovadoPor;
}
