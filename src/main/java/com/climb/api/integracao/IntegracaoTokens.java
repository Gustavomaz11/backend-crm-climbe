package com.climb.api.integracao;

import com.climb.api.security.TokenSeguro;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;

@Service
public class IntegracaoTokens {
    private final SecretKey key;
    public IntegracaoTokens(@Value("${app.integracoes.chave:${jwt.secret}}") String chave) {
        if (chave == null || chave.length() < 32) throw new IllegalStateException("Configure uma chave de integração com pelo menos 32 caracteres.");
        key = Keys.hmacShaKeyFor(TokenSeguro.sha256("climbe-integracoes-v1:" + chave));
    }
    public String acesso(IntegracaoCliente c) {
        Instant limite = Instant.now().plusSeconds(900);
        return assinar(c, "acesso", UUID.randomUUID().toString(), c.getExpiraEm().isBefore(limite) ? c.getExpiraEm() : limite);
    }
    public String ativacao(IntegracaoCliente c) {
        return assinar(c, "ativacao", c.getAtivacaoNonce(), c.getAtivacaoExpiraEm());
    }
    private String assinar(IntegracaoCliente c, String tipo, String nonce, Instant validade) {
        return Jwts.builder().setIssuer("climbe-integracoes").setAudience("climbe-api-externa")
                .setSubject(c.getId()).setId(nonce).claim("tipo", tipo).claim("versao", c.getVersao())
                .setExpiration(Date.from(validade)).signWith(key, SignatureAlgorithm.HS256).compact();
    }
    public Claims validar(String token, String tipo) {
        try {
            var jwt = Jwts.parserBuilder().setSigningKey(key).requireIssuer("climbe-integracoes")
                    .requireAudience("climbe-api-externa").require("tipo", tipo).build().parseClaimsJws(token);
            if (!"HS256".equals(jwt.getHeader().getAlgorithm()) || jwt.getBody().getExpiration() == null
                    || jwt.getBody().getSubject() == null || jwt.getBody().getId() == null) throw new IllegalArgumentException();
            return jwt.getBody();
        } catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Credencial inválida ou expirada.");
        }
    }
}
