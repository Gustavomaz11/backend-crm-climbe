package com.climb.api.service;

import com.climb.api.config.ZapSignProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ZapSignClientTest {
    @Test
    void permiteQueSpringInjeteConstrutorDeProducao() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ZapSignProperties.class, this::properties);
            context.register(ZapSignClient.class);
            context.refresh();

            assertThat(context.getBean(ZapSignClient.class)).isNotNull();
        }
    }

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

    @Test
    void informaQuandoPlanoDeApiForObrigatorio() {
        ZapSignProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZapSignClient client = new ZapSignClient(properties, builder.build());
        server.expect(once(), requestTo("https://api.zapsign.com.br/api/v1/docs/"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("API plan required"));

        assertThatThrownBy(() -> client.criarDocumento(
                "contrato.pdf", new byte[]{1}, "Cliente", "cliente@example.com",
                null, "https://app.example.com/revisao/token", "revisao-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).contains("plano de API da ZapSign");
                });
        server.verify();
    }

    @Test
    void informaCampoRecusadoSemExporDadosSensiveis() {
        ZapSignProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZapSignClient client = new ZapSignClient(properties, builder.build());
        server.expect(once(), requestTo("https://api.zapsign.com.br/api/v1/docs/"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"redirect_link\":[\"Informe uma URL válida para cliente@example.com\"]}"));

        assertThatThrownBy(() -> client.criarDocumento(
                "contrato.pdf", new byte[]{1}, "Cliente", "cliente@example.com",
                null, "url-invalida", "revisao-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getReason()).contains("redirect_link");
                    assertThat(exception.getReason()).contains("<email>");
                    assertThat(exception.getReason()).doesNotContain("cliente@example.com");
                });
        server.verify();
    }

    @Test
    void enviaFlagSandboxQuandoConfigurada() {
        ZapSignProperties properties = properties();
        properties.setSandbox(true);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ZapSignClient client = new ZapSignClient(properties, builder.build());
        server.expect(once(), requestTo("https://api.zapsign.com.br/api/v1/docs/"))
                .andExpect(content().json("{\"sandbox\":true}"))
                .andRespond(withSuccess("""
                        {"token":"doc-token","status":"pending",
                         "signers":[{"token":"signer-token","status":"new"}]}
                        """, MediaType.APPLICATION_JSON));

        ZapSignClient.Documento documento = client.criarDocumento(
                "contrato.pdf", new byte[]{1}, "Cliente", "cliente@example.com",
                null, "https://app.example.com/revisao/token", "revisao-1");

        assertThat(documento.token()).isEqualTo("doc-token");
        server.verify();
    }

    private ZapSignProperties properties() {
        ZapSignProperties properties = new ZapSignProperties();
        properties.setApiUrl("https://api.zapsign.com.br");
        properties.setApiToken("token-teste");
        return properties;
    }
}
