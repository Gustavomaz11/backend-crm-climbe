package com.climb.api.controller;

import com.climb.api.model.AuthStatus;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.ExchangeCodeRequestDTO;
import com.climb.api.model.dto.ExchangeCodeResponseDTO;
import com.climb.api.model.dto.LoginRequestDTO;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.RefreshTokenRequestDTO;
import com.climb.api.model.dto.RefreshTokenResponseDTO;
import com.climb.api.service.AuthenticationService;
import com.climb.api.service.GoogleOAuthService;
import com.climb.api.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
    public ResponseEntity<ApiResponse<RefreshTokenResponseDTO>> refresh(@jakarta.validation.Valid @RequestBody RefreshTokenRequestDTO dto) {
        AuthResult<RefreshTokenResponseDTO> result = authenticationService.refreshAccessToken(dto.getRefreshToken());
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.ok(result.data(), "Token renovado com sucesso"));
        }
        return ResponseEntity.status(httpStatusFor(result.status()))
                .body(ApiResponse.error(result.message()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@jakarta.validation.Valid @RequestBody RefreshTokenRequestDTO dto) {
        authenticationService.encerrarSessao(dto.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<ExchangeCodeResponseDTO>> exchangeCode(
            @jakarta.validation.Valid @RequestBody ExchangeCodeRequestDTO dto) {
        log.info("POST /auth/exchange — exchange code: {}", LogSanitizer.oauthCodeForLog(dto.code()));
        try {
            ExchangeCodeResponseDTO response = googleOAuthService.exchangeCode(dto.code(), dto.codeVerifier());
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
