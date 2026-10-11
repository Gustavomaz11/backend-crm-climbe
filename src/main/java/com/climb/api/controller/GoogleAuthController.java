package com.climb.api.controller;

import com.climb.api.model.dto.GoogleAuthorizationUrlResponseDTO;
import com.climb.api.service.GoogleOAuthService;
import com.climb.api.service.OAuthLoginRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Duration;

@RestController @RequestMapping("/auth/google")
public class GoogleAuthController {
    public static final String BINDING_COOKIE = "CLIMB_OAUTH_BINDING";
    private final GoogleOAuthService google;
    private final OAuthLoginRequestService requests;
    public GoogleAuthController(GoogleOAuthService google, OAuthLoginRequestService requests) { this.google = google; this.requests = requests; }

    @GetMapping("/start")
    public ResponseEntity<Void> iniciar(@RequestParam String codeChallenge, HttpServletRequest request) {
        var start = requests.iniciar(codeChallenge);
        var url = google.gerarUrlAutorizacao(start.state(), start.googleChallenge());
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url.authorizationUrl())
                .header(HttpHeaders.SET_COOKIE, cookie(start.browserBinding(), request, 600)).build();
    }

    @GetMapping("/url")
    public ResponseEntity<GoogleAuthorizationUrlResponseDTO> url(@RequestParam String codeChallenge, HttpServletRequest request) {
        var start = requests.iniciar(codeChallenge);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(start.browserBinding(), request, 600))
                .body(google.gerarUrlAutorizacao(start.state(), start.googleChallenge()));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String error,
            @RequestParam(required = false) String state, @CookieValue(value = BINDING_COOKIE, required = false) String binding,
            HttpServletRequest request) {
        URI redirect;
        try {
            var validado = requests.consumir(state, binding);
            if (error != null || code == null || code.isBlank()) {
                redirect = google.gerarRedirecionamentoErro("Não foi possível concluir a autenticação com Google. Tente novamente.");
            } else redirect = google.resolverCallbackGoogle(code, validado.codeVerifier(), validado.appChallenge());
        } catch (RuntimeException e) {
            redirect = google.gerarRedirecionamentoErro("A autenticação expirou ou não foi iniciada neste navegador. Tente novamente.");
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirect.toASCIIString())
                .header(HttpHeaders.SET_COOKIE, cookie("", request, 0)).build();
    }

    private String cookie(String value, HttpServletRequest request, int seconds) {
        return ResponseCookie.from(BINDING_COOKIE, value).httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                .path("/auth/google").maxAge(Duration.ofSeconds(seconds)).build().toString();
    }
}
