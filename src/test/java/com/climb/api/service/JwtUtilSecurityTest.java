package com.climb.api.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtUtilSecurityTest {
    static final String KEY = "isolated-test-only-signing-key-at-least-256-bits";
    static JwtUtil configuredJwt() {
        var jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secretKey", KEY);
        ReflectionTestUtils.setField(jwt, "issuer", "climb-api");
        ReflectionTestUtils.setField(jwt, "audience", "climb-api");
        ReflectionTestUtils.setField(jwt, "accessTokenExpiration", 7200000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpiration", 604800000L);
        ReflectionTestUtils.setField(jwt, "pendingTokenExpiration", 1800000L);
        jwt.validarConfiguracao();
        return jwt;
    }

    @Test void aceitaOsTresTiposSomenteComClaimsObrigatorios() {
        var jwt = configuredJwt(); var sid = UUID.randomUUID().toString();
        assertThat(jwt.validateToken(jwt.generateAccessToken(7L, "user@test.local", sid))).isTrue();
        assertThat(jwt.validateToken(jwt.generateRefreshToken(7L, "user@test.local", sid, Instant.now().plusSeconds(3600)))).isTrue();
        assertThat(jwt.validateToken(jwt.generatePendingRegistrationToken(8L, "pending@test.local"))).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"iss", "aud", "exp", "iat", "sub", "jti", "type", "userId", "sid"})
    void rejeitaClaimAusenteMesmoComAssinaturaValida(String missing) {
        var claims = claims(); claims.remove(missing);
        assertThat(configuredJwt().validateToken(sign(claims, SignatureAlgorithm.HS256, KEY))).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"iss", "aud", "exp", "iat", "sub", "jti", "type", "userId", "sid"})
    void rejeitaClaimInvalidaMesmoComAssinaturaValida(String invalid) {
        var claims = claims();
        claims.put(invalid, switch(invalid) {
            case "exp" -> new Date(System.currentTimeMillis()-60000);
            case "iat" -> new Date(System.currentTimeMillis()+60000);
            case "userId" -> -1;
            case "sub", "jti" -> "";
            default -> "valor-invalido";
        });
        assertThat(configuredJwt().validateToken(sign(claims, SignatureAlgorithm.HS256, KEY))).isFalse();
    }

    @Test void rejeitaAlgoritmoAlternativoAssinaturaErradaETokenSemAssinatura() {
        var jwt = configuredJwt();
        assertThat(jwt.validateToken(sign(claims(), SignatureAlgorithm.HS384, KEY+KEY))).isFalse();
        assertThat(jwt.validateToken(sign(claims(), SignatureAlgorithm.HS256, KEY+"outro"))).isFalse();
        assertThat(jwt.validateToken(Jwts.builder().setClaims(claims()).compact())).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"", "curta", "your-secret-key-change-in-production-minimum-256-bits-long"})
    void naoIniciaComChaveAusenteCurtaOuPadrao(String key) {
        var jwt = configuredJwt(); ReflectionTestUtils.setField(jwt, "secretKey", key);
        assertThatThrownBy(jwt::validarConfiguracao).isInstanceOf(IllegalStateException.class);
    }

    private Map<String,Object> claims() {
        var c = new HashMap<String,Object>();
        c.put("iss", "climb-api"); c.put("aud", "climb-api"); c.put("sub", "user@test.local");
        c.put("jti", UUID.randomUUID().toString()); c.put("iat", new Date());
        c.put("exp", new Date(System.currentTimeMillis()+600000));
        c.put("type", JwtUtil.TYPE_ACCESS); c.put("userId", 7L); c.put("sid", UUID.randomUUID().toString());
        return c;
    }
    private String sign(Map<String,Object> claims, SignatureAlgorithm algorithm, String key) {
        return Jwts.builder().setClaims(claims).signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)), algorithm).compact();
    }
}
