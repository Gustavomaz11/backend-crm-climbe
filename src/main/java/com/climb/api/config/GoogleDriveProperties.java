package com.climb.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.drive")
@Getter @Setter
public class GoogleDriveProperties {
    private boolean enabled = true;
    private String rootFolderId = "1Ea1yJuqRd7jfSAa_aC58qyl_Dsv9qmVB";
    private String accountEmail;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
}
