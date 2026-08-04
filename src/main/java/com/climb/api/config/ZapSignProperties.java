package com.climb.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.zapsign")
public class ZapSignProperties {
    private String apiUrl = "https://api.zapsign.com.br";
    private String apiToken;
    private String signingUrl = "https://app.zapsign.com.br/verificar";
    private String webhookSecret;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public String getSigningUrl() { return signingUrl; }
    public void setSigningUrl(String signingUrl) { this.signingUrl = signingUrl; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
}
