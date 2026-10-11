package com.climb.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class JwtUtil {
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String TYPE_PENDING_REGISTRATION = "pending_registration";
    @Value("${jwt.secret}") private String secretKey;
    @Value("${jwt.expiration.access-token}") private long accessTokenExpiration;
    @Value("${jwt.expiration.refresh-token}") private long refreshTokenExpiration;
    @Value("${jwt.expiration.pending-token}") private long pendingTokenExpiration;
    @Value("${jwt.issuer}") private String issuer;
    @Value("${jwt.audience}") private String audience;

    @PostConstruct
    public void validarConfiguracao() {
        getSigningKey();
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || accessTokenExpiration <= 0 || refreshTokenExpiration <= 0 || pendingTokenExpiration <= 0) {
            throw new IllegalStateException("Configure emissor, audiência e validade dos tokens JWT.");
        }
    }
    private SecretKey getSigningKey() {
        if (secretKey == null || secretKey.isBlank() || secretKey.getBytes(StandardCharsets.UTF_8).length < 32
                || secretKey.equals("your-secret-key-change-in-production-minimum-256-bits-long")) {
            throw new IllegalStateException("Configure JWT_SECRET com uma chave aleatória de pelo menos 256 bits.");
        }
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
    public String generateAccessToken(Long usuarioId, String email, String sessionId) {
        return buildToken(Map.of("userId", usuarioId, "type", TYPE_ACCESS, "sid", sessionId), email,
                Instant.now().plusMillis(accessTokenExpiration));
    }
    public String generateRefreshToken(Long usuarioId, String email, String sessionId, Instant expiresAt) {
        return buildToken(Map.of("userId", usuarioId, "type", TYPE_REFRESH, "sid", sessionId), email, expiresAt);
    }
    public String generatePendingRegistrationToken(Long pendingId, String email) {
        return buildToken(Map.of("pendingId", pendingId, "type", TYPE_PENDING_REGISTRATION), email,
                Instant.now().plusMillis(pendingTokenExpiration));
    }
    private String buildToken(Map<String, Object> claims, String subject, Instant expiresAt) {
        return Jwts.builder().setClaims(claims).setSubject(subject).setIssuer(issuer).setAudience(audience)
                .setId(UUID.randomUUID().toString()).setIssuedAt(new Date()).setExpiration(Date.from(expiresAt))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }
    public boolean validateToken(String token) {
        try { parseValidatedToken(token); return true; }
        catch (RuntimeException e) { return false; }
    }
    public Claims parseValidatedToken(String token) {
        var parsed = Jwts.parserBuilder().setSigningKey(getSigningKey()).requireIssuer(issuer).requireAudience(audience)
                .build().parseClaimsJws(token);
        Claims claims = parsed.getBody();
        if (!"HS256".equals(parsed.getHeader().getAlgorithm()) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getId() == null || claims.getId().isBlank()
                || claims.getIssuedAt().after(new Date(System.currentTimeMillis() + 30000))
                || !claims.getExpiration().after(claims.getIssuedAt())) throw new IllegalArgumentException("JWT inválido");
        String type = claims.get("type", String.class);
        if (TYPE_PENDING_REGISTRATION.equals(type)) {
            Long id = claims.get("pendingId", Long.class);
            if (id == null || id <= 0) throw new IllegalArgumentException("Cadastro inválido");
        } else if (TYPE_ACCESS.equals(type) || TYPE_REFRESH.equals(type)) {
            Long id = claims.get("userId", Long.class);
            String sid = claims.get("sid", String.class);
            if (id == null || id <= 0 || sid == null) throw new IllegalArgumentException("Sessão inválida");
            UUID.fromString(sid);
        } else throw new IllegalArgumentException("Tipo de JWT inválido");
        return claims;
    }
    public Long extractUserId(String token) { return extractClaim(token, c -> c.get("userId", Long.class)); }
    public Long extractPendingId(String token) { return extractClaim(token, c -> c.get("pendingId", Long.class)); }
    public String extractSessionId(String token) { return extractClaim(token, c -> c.get("sid", String.class)); }
    public String extractEmail(String token) { return extractClaim(token, Claims::getSubject); }
    public String extractTokenType(String token) { return extractClaim(token, c -> c.get("type", String.class)); }
    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> resolver) { return resolver.apply(parseValidatedToken(token)); }
    public boolean isTokenExpired(String token) { return !validateToken(token); }
    public long getAccessTokenExpirationTime() { return accessTokenExpiration; }
    public long getRefreshTokenExpirationTime() { return refreshTokenExpiration; }
    public long getPendingTokenExpirationTime() { return pendingTokenExpiration; }
}
