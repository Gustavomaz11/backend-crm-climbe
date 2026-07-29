package com.climb.api.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração de integração com Google Calendar API.
 *
 * As propriedades são resolvidas via application.yml com fallback para string vazia,
 * portanto o bean é criado normalmente em qualquer ambiente — inclusive sem credenciais.
 * A integração só funciona de fato quando GOOGLE_CALENDAR_CLIENT_ID estiver definido.
 */
@Configuration
public class GoogleCalendarConfig {

    @Value("${google.calendar.client-id:}")
    private String clientId;

    @Value("${google.calendar.client-secret:}")
    private String clientSecret;

    @Value("${google.calendar.redirect-uri:}")
    private String redirectUri;

    @Value("${google.calendar.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${google.calendar.allowed-domain:@climbe.com.br}")
    private String allowedDomain;

    public String getClientId()     { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri()  { return redirectUri; }
    public String getFrontendUrl()  { return frontendUrl; }
    public String getAllowedDomain() {
        if (allowedDomain == null || allowedDomain.isBlank()) {
            return "@climbe.com.br";
        }

        String normalized = allowedDomain.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("@") ? normalized : "@" + normalized;
    }

    public boolean isEmailAllowed(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return email.trim().toLowerCase(Locale.ROOT).endsWith(getAllowedDomain());
    }

    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank();
    }
}
