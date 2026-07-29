package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "usuario_oauth")
public class UsuarioOAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "email_provider")
    private String emailProvider;

    @Column(name = "nome_provider")
    private String nomeProvider;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "vinculado_em", nullable = false)
    private LocalDateTime vinculadoEm;

    @Column(name = "access_token_criptografado", length = 4096)
    private String accessTokenCriptografado;

    @Column(name = "refresh_token_criptografado", length = 4096)
    private String refreshTokenCriptografado;

    @Column(name = "access_token_expira_em")
    private LocalDateTime accessTokenExpiraEm;

    @Column(length = 1000)
    private String scopes;
}
