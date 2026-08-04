package com.climb.api.service;

import com.climb.api.config.ZapSignProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ZapSignClientTest {
    @Test
    void informaQuandoCredencialForRecusada() {
        ZapSignProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZapSignClient client = new ZapSignClient(properties, builder.build());
        server.expect(once(), requestTo("https://api.zapsign.com.br/api/v1/docs/"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Invalid token\"}"));

        assertThatThrownBy(() -> client.criarDocumento(
                "contrato.pdf", new byte[]{1}, "Cliente", "cliente@example.com",
                null, "https://app.example.com/revisao/token", "revisao-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).contains("credencial da ZapSign foi recusada");
                });
        server.verify();
    }

    private ZapSignProperties properties() {
        ZapSignProperties properties = new ZapSignProperties();
        properties.setApiUrl("https://api.zapsign.com.br");
        properties.setApiToken("token-teste");
        return properties;
    }
}
