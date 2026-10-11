package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @Entity @Table(name = "oauth_login_requests")
public class OAuthLoginRequest {
    @Id @Column(name = "state_hash", length = 64) private String stateHash;
    @Column(name = "browser_hash", nullable = false, length = 64) private String browserHash;
    @Column(name = "code_verifier", nullable = false, length = 128) private String codeVerifier;
    @Column(name = "app_challenge", nullable = false, length = 43) private String appChallenge;
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
}
