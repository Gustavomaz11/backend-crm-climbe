package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.RefreshTokenResponseDTO;
import com.climb.api.repository.SessaoAutenticacaoRepository;
import com.climb.api.security.TokenSeguro;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessaoAutenticacaoService {
    private final SessaoAutenticacaoRepository sessoes;
    private final JwtUtil jwt;
    public SessaoAutenticacaoService(SessaoAutenticacaoRepository sessoes, JwtUtil jwt) { this.sessoes = sessoes; this.jwt = jwt; }

    @Transactional
    public RefreshTokenResponseDTO criar(Usuario usuario) {
        var sessao = new SessaoAutenticacao();
        sessao.setId(UUID.randomUUID().toString()); sessao.setUsuario(usuario);
        sessao.setVersaoAutenticacao(usuario.getVersaoAutenticacao());
        sessao.setCriadoEm(Instant.now()); sessao.setExpiraEm(Instant.now().plusMillis(jwt.getRefreshTokenExpirationTime()));
        var tokens = gerar(sessao);
        sessoes.save(sessao);
        return tokens;
    }

    @Transactional(readOnly = true)
    public boolean estaAtiva(String id, Long usuarioId) {
        return id != null && usuarioId != null && sessoes.estaAtiva(id, usuarioId, Instant.now());
    }

    @Transactional
    public Optional<RefreshTokenResponseDTO> renovar(String token) {
        if (!jwt.validateToken(token) || !JwtUtil.TYPE_REFRESH.equals(jwt.extractTokenType(token))) return Optional.empty();
        var sessao = sessoes.buscarParaAtualizar(jwt.extractSessionId(token)).orElse(null);
        if (!valida(sessao, jwt.extractUserId(token))) return Optional.empty();
        if (!TokenSeguro.iguais(sessao.getRefreshHash(), TokenSeguro.hash(token))) {
            sessao.setRevogadaEm(Instant.now()); sessoes.save(sessao);
            return Optional.empty();
        }
        var tokens = gerar(sessao); sessoes.save(sessao);
        return Optional.of(tokens);
    }

    @Transactional
    public void encerrar(String refreshToken) {
        if (!jwt.validateToken(refreshToken) || !JwtUtil.TYPE_REFRESH.equals(jwt.extractTokenType(refreshToken))) return;
        sessoes.buscarParaAtualizar(jwt.extractSessionId(refreshToken))
                .filter(s -> s.getUsuario().getId().equals(jwt.extractUserId(refreshToken)))
                .ifPresent(s -> { s.setRevogadaEm(Instant.now()); sessoes.save(s); });
    }

    private boolean valida(SessaoAutenticacao s, Long usuarioId) {
        return s != null && s.getUsuario().getId().equals(usuarioId) && s.getRevogadaEm() == null
                && s.getExpiraEm().isAfter(Instant.now()) && "ATIVO".equals(s.getUsuario().getSituacao())
                && s.getVersaoAutenticacao() == s.getUsuario().getVersaoAutenticacao();
    }
    private RefreshTokenResponseDTO gerar(SessaoAutenticacao sessao) {
        Usuario u = sessao.getUsuario();
        String refresh = jwt.generateRefreshToken(u.getId(), u.getEmail(), sessao.getId(), sessao.getExpiraEm());
        sessao.setRefreshHash(TokenSeguro.hash(refresh));
        return new RefreshTokenResponseDTO(jwt.generateAccessToken(u.getId(), u.getEmail(), sessao.getId()), refresh, jwt.getAccessTokenExpirationTime());
    }
}
