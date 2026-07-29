package com.climb.api.service;

import com.climb.api.config.GoogleCalendarConfig;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.UsuarioOAuth;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class GoogleCredentialService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCredentialService.class);
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final UsuarioOAuthRepository usuarioOAuthRepository;
    private final OAuth2PendingRegistrationRepository pendingRepository;
    private final TokenEncryptionService encryptionService;
    private final GoogleCalendarConfig config;
    private final RestClient restClient;

    public GoogleCredentialService(UsuarioOAuthRepository usuarioOAuthRepository,
                                   OAuth2PendingRegistrationRepository pendingRepository,
                                   TokenEncryptionService encryptionService,
                                   GoogleCalendarConfig config) {
        this.usuarioOAuthRepository = usuarioOAuthRepository;
        this.pendingRepository = pendingRepository;
        this.encryptionService = encryptionService;
        this.config = config;
        this.restClient = RestClient.builder().build();
    }

    @Transactional
    public void salvarParaUsuario(Long usuarioId,
                                  String accessToken,
                                  String refreshToken,
                                  Long expiresIn,
                                  String scopes) {
        UsuarioOAuth vinculo = usuarioOAuthRepository
                .findByUsuarioIdAndProvider(usuarioId, OAuthProvider.GOOGLE)
                .orElseThrow(() -> new IllegalStateException("Conta Google nao vinculada ao usuario"));
        exigirContaCorporativa(vinculo.getEmailProvider());
        aplicarCredenciais(vinculo, accessToken, refreshToken, expiresIn, scopes);
        usuarioOAuthRepository.save(vinculo);
    }

    @Transactional
    public void salvarParaPending(OAuth2PendingRegistration pending,
                                  String accessToken,
                                  String refreshToken,
                                  Long expiresIn,
                                  String scopes) {
        exigirContaCorporativa(pending.getEmail());
        pending.setAccessTokenCriptografado(encryptionService.criptografar(accessToken));
        if (refreshToken != null && !refreshToken.isBlank()) {
            pending.setRefreshTokenCriptografado(encryptionService.criptografar(refreshToken));
        }
        pending.setAccessTokenExpiraEm(calcularExpiracao(expiresIn));
        pending.setScopes(scopes);
        pendingRepository.save(pending);
    }

    @Transactional
    public void transferirPendingParaVinculo(OAuth2PendingRegistration pending, UsuarioOAuth vinculo) {
        exigirContaCorporativa(vinculo.getEmailProvider());
        vinculo.setAccessTokenCriptografado(pending.getAccessTokenCriptografado());
        vinculo.setRefreshTokenCriptografado(pending.getRefreshTokenCriptografado());
        vinculo.setAccessTokenExpiraEm(pending.getAccessTokenExpiraEm());
        vinculo.setScopes(pending.getScopes());
        usuarioOAuthRepository.save(vinculo);
    }

    @Transactional
    public Optional<String> obterAccessToken(Long usuarioId) {
        UsuarioOAuth vinculo = usuarioOAuthRepository
                .findByUsuarioIdAndProvider(usuarioId, OAuthProvider.GOOGLE)
                .orElse(null);
        if (vinculo == null || vinculo.getAccessTokenCriptografado() == null) {
            return Optional.empty();
        }
        if (!config.isEmailAllowed(vinculo.getEmailProvider())) {
            log.warn("Credencial Google ignorada para o usuario {} por nao pertencer ao dominio corporativo",
                    usuarioId);
            return Optional.empty();
        }

        if (vinculo.getAccessTokenExpiraEm() == null
                || vinculo.getAccessTokenExpiraEm().isAfter(LocalDateTime.now().plusMinutes(1))) {
            return Optional.ofNullable(encryptionService.descriptografar(vinculo.getAccessTokenCriptografado()));
        }

        return renovarAccessToken(vinculo);
    }

    private Optional<String> renovarAccessToken(UsuarioOAuth vinculo) {
        String refreshToken = encryptionService.descriptografar(vinculo.getRefreshTokenCriptografado());
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(GOOGLE_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("access_token") == null) {
                return Optional.empty();
            }

            String accessToken = response.get("access_token").toString();
            Long expiresIn = response.get("expires_in") instanceof Number number ? number.longValue() : 3600L;
            aplicarCredenciais(vinculo, accessToken, null, expiresIn,
                    response.get("scope") != null ? response.get("scope").toString() : vinculo.getScopes());
            usuarioOAuthRepository.save(vinculo);
            return Optional.of(accessToken);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel renovar o token Google do usuario {}: {}",
                    vinculo.getUsuario().getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private void aplicarCredenciais(UsuarioOAuth vinculo,
                                    String accessToken,
                                    String refreshToken,
                                    Long expiresIn,
                                    String scopes) {
        vinculo.setAccessTokenCriptografado(encryptionService.criptografar(accessToken));
        if (refreshToken != null && !refreshToken.isBlank()) {
            vinculo.setRefreshTokenCriptografado(encryptionService.criptografar(refreshToken));
        }
        vinculo.setAccessTokenExpiraEm(calcularExpiracao(expiresIn));
        vinculo.setScopes(scopes);
    }

    private LocalDateTime calcularExpiracao(Long expiresIn) {
        return LocalDateTime.now().plusSeconds(expiresIn != null ? expiresIn : 3600L);
    }

    private void exigirContaCorporativa(String email) {
        if (!config.isEmailAllowed(email)) {
            throw new IllegalArgumentException("Use uma conta Google corporativa " + config.getAllowedDomain());
        }
    }
}
