package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.model.OAuthProvider;
import com.climb.api.repository.UsuarioOAuthRepository;
import com.google.auth.oauth2.UserCredentials;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Arrays;

@Service
public class GoogleDriveCredentialService {
    private final GoogleDriveProperties properties;
    private final UsuarioOAuthRepository vinculos;
    private final GoogleCredentialService credentials;
    private UserCredentials contaTecnica;

    public GoogleDriveCredentialService(GoogleDriveProperties properties, UsuarioOAuthRepository vinculos,
            GoogleCredentialService credentials) {
        this.properties = properties; this.vinculos = vinculos; this.credentials = credentials;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public synchronized String obterAccessToken() {
        if (!properties.isEnabled()) throw indisponivel("A integração com o Drive está desativada.");
        if (properties.getRefreshToken() != null && !properties.getRefreshToken().isBlank()) return tokenContaTecnica();
        String email = properties.getAccountEmail();
        if (email == null || email.isBlank()) throw indisponivel("Configure a conta responsável pelo Drive.");
        var vinculo = vinculos.findByProviderAndEmailProviderIgnoreCase(OAuthProvider.GOOGLE, email.trim())
                .orElseThrow(() -> indisponivel("Entre com a conta responsável pelo Drive para autorizar a integração."));
        boolean permitido = vinculo.getScopes() != null && Arrays.asList(vinculo.getScopes().split("\\s+"))
                .contains("https://www.googleapis.com/auth/drive");
        if (!permitido) throw indisponivel("Entre novamente com a conta responsável e autorize o acesso ao Drive.");
        return credentials.obterAccessToken(vinculo.getUsuario().getId())
                .orElseThrow(() -> indisponivel("Renove a autorização Google da conta responsável pelo Drive."));
    }

    private String tokenContaTecnica() {
        if (properties.getClientId() == null || properties.getClientId().isBlank()
                || properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw indisponivel("Configure o cliente OAuth da integração com o Drive.");
        }
        try {
            if (contaTecnica == null) contaTecnica = UserCredentials.newBuilder().setClientId(properties.getClientId())
                    .setClientSecret(properties.getClientSecret()).setRefreshToken(properties.getRefreshToken()).build();
            contaTecnica.refreshIfExpired();
            return contaTecnica.getAccessToken().getTokenValue();
        } catch (IOException exception) {
            throw indisponivel("Não foi possível renovar a autorização da conta responsável pelo Drive.");
        }
    }

    private ResponseStatusException indisponivel(String mensagem) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
    }
}
