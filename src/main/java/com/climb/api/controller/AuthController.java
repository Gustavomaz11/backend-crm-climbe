package com.climb.api.controller;

import com.climb.api.model.AuthStatus;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.ExchangeCodeRequestDTO;
import com.climb.api.model.dto.ExchangeCodeResponseDTO;
import com.climb.api.model.dto.GoogleAuthorizationUrlResponseDTO;
import com.climb.api.model.dto.LoginRequestDTO;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.RefreshTokenRequestDTO;
import com.climb.api.service.AuthenticationService;
import com.climb.api.service.GoogleOAuthService;
import com.climb.api.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;
    private final GoogleOAuthService googleOAuthService;

    public AuthController(AuthenticationService authenticationService, GoogleOAuthService googleOAuthService) {
        this.authenticationService = authenticationService;
        this.googleOAuthService = googleOAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@RequestBody LoginRequestDTO dto) {
        AuthResult<LoginResponseDTO> result = authenticationService.autenticar(dto.getEmail(), dto.getSenha());
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.ok(result.data(), "Login realizado com sucesso"));
        }
        return ResponseEntity.status(httpStatusFor(result.status()))
                .body(ApiResponse.error(result.message()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(@RequestBody RefreshTokenRequestDTO dto) {
        AuthResult<String> result = authenticationService.refreshAccessToken(dto.getRefreshToken());
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.ok(result.data(), "Token renovado com sucesso"));
        }
        return ResponseEntity.status(httpStatusFor(result.status()))
                .body(ApiResponse.error(result.message()));
    }

    @GetMapping("/google/url")
    public ResponseEntity<?> googleAuthorizationUrl() {
        try {
            GoogleAuthorizationUrlResponseDTO response = googleOAuthService.gerarUrlAutorizacao();
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        log.info("GET /auth/google/callback — error param: {}, authorization code: {}",
                error == null || error.isBlank() ? "absent" : "present(length=" + error.length() + ")",
                LogSanitizer.oauthCodeForLog(code));
        try {
            if (error != null && !error.isBlank()) {
                URI redirectUri = googleOAuthService.gerarRedirecionamentoErro("Google retornou erro: " + error);
                return redirect(redirectUri);
            }

            if (code == null || code.isBlank()) {
                URI redirectUri = googleOAuthService.gerarRedirecionamentoErro("Parametro code e obrigatorio");
                return redirect(redirectUri);
            }

            URI redirectUri = googleOAuthService.resolverCallbackGoogle(code);
            return redirect(redirectUri);

        } catch (RuntimeException e) {
            URI redirectUri = googleOAuthService.gerarRedirecionamentoErro(e.getMessage());
            return redirect(redirectUri);
        }
    }

    private ResponseEntity<Void> redirect(URI uri) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, uri.toASCIIString())
                .build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<ExchangeCodeResponseDTO>> exchangeCode(
            @RequestBody ExchangeCodeRequestDTO dto) {
        log.info("POST /auth/exchange — exchange code: {}", LogSanitizer.oauthCodeForLog(dto.code()));
        try {
            ExchangeCodeResponseDTO response = googleOAuthService.exchangeCode(dto.code());
            return ResponseEntity.ok(ApiResponse.ok(response, "Tokens obtidos com sucesso"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    private static HttpStatus httpStatusFor(AuthStatus status) {
        return switch (status) {
            case SUCCESS -> HttpStatus.OK;
            case INVALID_CREDENTIALS,
                 USER_NOT_FOUND,
                 INVALID_REFRESH_TOKEN,
                 WRONG_TOKEN_TYPE -> HttpStatus.UNAUTHORIZED;
            case PENDING_APPROVAL,
                 COMPLETAR_CADASTRO,
                 INATIVO,
                 SITUACAO_INVALIDA -> HttpStatus.FORBIDDEN;
        };
    }
}
