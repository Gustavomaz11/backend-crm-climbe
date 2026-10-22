package com.climb.api.integracao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class IntegracaoPayloadAuditoriaTest {
    @Test void preservaPayloadEmUtf8EOcultaCredenciaisEmObjetosEListas() throws Exception {
        var request = requisicao("POST", "application/json;charset=UTF-8", """
                {"nomeEmpresa":"Integração & Comércio","responsavelId":0,"observacoes":"<script>exemplo</script>",
                 "client_secret":"segredo-exemplo","extra":{"accessToken":"token-exemplo","email":"contato@example.com"},
                 "itens":[{"senha":"senha-exemplo","Authorization":"Bearer segredo","api_key":"chave-exemplo","privateKey":"privada-exemplo"}]}
                """);
        var captura = IntegracaoPayloadAuditoria.capturar(request, 201);
        var json = com.google.gson.JsonParser.parseString(captura.payload()).getAsJsonObject();
        assertThat(captura.limiteExcedido()).isFalse();
        assertThat(json.get("nomeEmpresa").getAsString()).isEqualTo("Integração & Comércio");
        assertThat(json.get("responsavelId").getAsInt()).isZero();
        assertThat(json.getAsJsonObject("extra").get("email").getAsString()).isEqualTo("contato@example.com");
        assertThat(captura.payload()).contains("[OCULTO]").doesNotContain("segredo-exemplo", "token-exemplo", "senha-exemplo", "Bearer segredo", "chave-exemplo", "privada-exemplo");
    }

    @ParameterizedTest @ValueSource(ints = {200, 204, 400, 401, 403, 415, 500})
    void naoArmazenaCorpoSemCriacaoConcluida(int status) throws Exception {
        assertThat(IntegracaoPayloadAuditoria.capturar(requisicao("POST", "application/json", "{\"nomeContato\":\"Contato\"}"), status).payload()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT", "PATCH", "DELETE"})
    void somentePostDeCriacaoPossuiPayload(String metodo) throws Exception {
        assertThat(IntegracaoPayloadAuditoria.capturar(requisicao(metodo, "application/json", "{\"nomeContato\":\"Contato\"}"), 201).payload()).isNull();
    }

    @Test void omiteCorpoAcimaDoLimiteInclusiveSemContentLength() throws Exception {
        var original = new MockHttpServletRequest("POST", "/api/integracoes/v1/comercial/pre-vendas/leads") {
            @Override public long getContentLengthLong() { return -1; }
        };
        original.setContentType("application/json");
        original.setContent(("{\"observacoes\":\"" + "á".repeat(IntegracaoPayloadAuditoria.LIMITE_BYTES) + "\"}").getBytes(StandardCharsets.UTF_8));
        var request = new ContentCachingRequestWrapper(original, IntegracaoPayloadAuditoria.LIMITE_BYTES + 1);
        request.getInputStream().readAllBytes();
        var captura = IntegracaoPayloadAuditoria.capturar(request, 201);
        assertThat(captura.payload()).isNull(); assertThat(captura.limiteExcedido()).isTrue();
        assertThat(request.getContentAsByteArray()).hasSize(IntegracaoPayloadAuditoria.LIMITE_BYTES + 1);
    }

    @Test void corpoInvalidoOuMuitoAninhadoNaoInterrompeUmaCriacao() throws Exception {
        for (String corpo : new String[]{"{invalido", "", "[".repeat(70) + "0" + "]".repeat(70)})
            assertThat(IntegracaoPayloadAuditoria.capturar(requisicao("POST", "application/json", corpo), 201).payload()).isNull();
        assertThat(IntegracaoPayloadAuditoria.capturar(requisicao("POST", "text/plain", "{}"), 201).payload()).isNull();
    }

    private ContentCachingRequestWrapper requisicao(String metodo, String tipo, String corpo) throws Exception {
        var original = new MockHttpServletRequest(metodo, "/api/integracoes/v1/comercial/pre-vendas/leads");
        original.setContentType(tipo); original.setContent(corpo.getBytes(StandardCharsets.UTF_8));
        var request = new ContentCachingRequestWrapper(original, IntegracaoPayloadAuditoria.LIMITE_BYTES + 1);
        request.getInputStream().readAllBytes();
        return request;
    }
}
