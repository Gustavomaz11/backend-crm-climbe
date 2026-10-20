package com.climb.api.controller;

import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.AvisosStreamService;
import com.climb.api.service.JwtUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class AvisosStreamController {
    private final AvisosStreamService stream;
    private final JwtUtil jwt;
    private final AuthenticatedUserProvider autenticado;

    public AvisosStreamController(AvisosStreamService stream, JwtUtil jwt, AuthenticatedUserProvider autenticado) {
        this.stream = stream; this.jwt = jwt; this.autenticado = autenticado;
    }

    @GetMapping(value = "/avisos/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> conectar(@RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer ") || !jwt.validateToken(authorization.substring("Bearer ".length()))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        var claims = jwt.parseValidatedToken(authorization.substring("Bearer ".length()));
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-store, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream.conectar(autenticado.getUserId(), claims.get("sid", String.class), claims.getExpiration().toInstant()));
    }
}
