package com.climb.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String TYPE_PENDING_REGISTRATION = "pending_registration";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration.access-token}")
    private long accessTokenExpiration;

    @Value("${jwt.expiration.refresh-token}")
    private long refreshTokenExpiration;

    @Value("${jwt.expiration.pending-token}")
    private long pendingTokenExpiration;

    @Value("${jwt.issuer}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long usuarioId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", usuarioId);
        claims.put("email", email);
        claims.put("type", TYPE_ACCESS);
        return buildToken(claims, email, accessTokenExpiration);
    }

    public String generateRefreshToken(Long usuarioId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", usuarioId);
        claims.put("email", email);
        claims.put("type", TYPE_REFRESH);
        return buildToken(claims, email, refreshTokenExpiration);
    }

    public String generatePendingRegistrationToken(Long pendingId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("pendingId", pendingId);
        claims.put("email", email);
        claims.put("type", TYPE_PENDING_REGISTRATION);
        return buildToken(claims, email, pendingTokenExpiration);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMillis) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Long extractPendingId(String token) {
        return extractClaim(token, claims -> claims.get("pendingId", Long.class));
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenExpired(String token) {
        try {
            final Date expiration = extractClaim(token, Claims::getExpiration);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public long getAccessTokenExpirationTime() {
        return accessTokenExpiration;
    }

    public long getPendingTokenExpirationTime() {
        return pendingTokenExpiration;
    }
}
