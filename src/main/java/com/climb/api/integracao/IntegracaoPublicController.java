package com.climb.api.integracao;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController @RequestMapping("/api/integracoes") @RequiredArgsConstructor
public class IntegracaoPublicController {
    private final IntegracaoService service;

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<IntegracaoDTO.Token> token(@RequestParam String grant_type,
            @RequestParam(required = false) String client_id, @RequestParam(required = false) String client_secret,
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!"client_credentials".equals(grant_type)) throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "grant_type deve ser client_credentials.");
        if (scope != null && !scope.isBlank()) throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "O escopo é definido pelo administrador da integração.");
        if (authorization != null && authorization.startsWith("Basic ")) {
            if (client_id != null || client_secret != null || authorization.length() > 2048)
                throw IntegracaoService.erro(HttpStatus.BAD_REQUEST, "Informe as credenciais em apenas um local.");
            try {
                String[] partes = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8).split(":", 2);
                if (partes.length != 2) throw new IllegalArgumentException();
                client_id = partes[0]; client_secret = partes[1];
            } catch (IllegalArgumentException e) { throw IntegracaoService.erro(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."); }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(service.autenticar(client_id, client_secret));
    }
    @PostMapping("/ativar")
    public ResponseEntity<IntegracaoDTO.Credenciais> ativar(@Valid @RequestBody IntegracaoDTO.Ativacao dto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(service.ativar(dto.token()));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, String>> erro(org.springframework.web.server.ResponseStatusException e) {
        String codigo = e.getStatusCode().value() == 401 ? "invalid_client" : e.getStatusCode().value() == 429 ? "temporarily_unavailable" : "invalid_request";
        var resposta = ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore()).header("Pragma", "no-cache");
        if (e.getStatusCode().value() == 401) resposta.header("WWW-Authenticate", "Basic realm=\"climbe-integracoes\"");
        if (e.getStatusCode().value() == 429) resposta.header("Retry-After", "60");
        return resposta.body(java.util.Map.of("error", codigo, "error_description", e.getReason() == null ? "Requisição inválida." : e.getReason(), "message", e.getReason() == null ? "Requisição inválida." : e.getReason()));
    }
}
