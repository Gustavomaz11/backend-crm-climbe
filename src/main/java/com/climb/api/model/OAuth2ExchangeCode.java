package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "oauth2_exchange_codes")
public class OAuth2ExchangeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "app_challenge", length = 43)
    private String appChallenge;

    @Column(name = "access_token", nullable = false, length = 2048)
    private String accessToken;

    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    @Column(name = "google_access_token", length = 2048)
    private String googleAccessToken;

    @Column(name = "google_refresh_token", length = 2048)
    private String googleRefreshToken;

    @Column(name = "expires_in")
    private Long expiresIn;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_status", length = 50)
    private String userStatus;

    @Column(name = "user_role", length = 100)
    private String userRole;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    private Boolean consumido = false;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
