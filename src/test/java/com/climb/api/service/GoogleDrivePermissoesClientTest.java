package com.climb.api.service;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleDrivePermissoesClientTest {
    private final Queue<String> respostas = new ArrayDeque<>();
    private final List<MockLowLevelHttpRequest> pedidos = new ArrayList<>();
    private final List<String> metodos = new ArrayList<>();
    private final MockHttpTransport transporte = new MockHttpTransport() {
        @Override public boolean supportsMethod(String metodo) { return !"PATCH".equals(metodo); }
        @Override public LowLevelHttpRequest buildRequest(String metodo, String url) {
            metodos.add(metodo);
            var resposta = new MockLowLevelHttpResponse().setStatusCode("DELETE".equals(metodo) ? 204 : 200)
                    .setContentType("application/json").setContent(respostas.isEmpty() ? "" : respostas.remove());
            var pedido = new MockLowLevelHttpRequest(url).setResponse(resposta); pedidos.add(pedido); return pedido;
        }
    };
    private GoogleDrivePermissoesClient client() {
        var credentials = mock(GoogleDriveCredentialService.class);
        when(credentials.obterAccessToken()).thenReturn("teste");
        return new GoogleDrivePermissoesClient(new GoogleDriveClient(credentials, transporte));
    }

    @Test void habilitaAcessoLimitadoEImpedeEditoresDeCompartilharPastaNoMeuDrive() throws Exception {
        respostas.add("{\"mimeType\":\"application/vnd.google-apps.folder\",\"inheritedPermissionsDisabled\":false,\"capabilities\":{\"canDisableInheritedPermissions\":true,\"canShare\":true}}");
        respostas.add("{\"id\":\"cfo\"}");
        client().limitar("cfo", true);
        assertThat(metodos).containsExactly("GET", "POST");
        assertThat(pedidos.getLast().getFirstHeaderValue("X-HTTP-Method-Override")).isEqualTo("PATCH");
        assertThat(pedidos.getLast().getContentAsString()).contains("\"inheritedPermissionsDisabled\":true", "\"writersCanShare\":false");
    }
    @Test void naoSimulaRestricaoQuandoContaNaoPodeLimitarHeranca() {
        respostas.add("{\"mimeType\":\"application/vnd.google-apps.folder\",\"capabilities\":{\"canDisableInheritedPermissions\":false,\"canShare\":true}}");
        assertThatThrownBy(() -> client().limitar("cfo", true)).hasMessageContaining("não pode alterar");
        assertThat(pedidos).hasSize(1);
    }
    @Test void paginaPermissoesEDistingueAcessoDiretoDeMetadadosHerdados() {
        respostas.add("{\"nextPageToken\":\"proxima\",\"permissions\":[{\"id\":\"grupo\",\"type\":\"group\",\"role\":\"reader\",\"view\":\"metadata\",\"permissionDetails\":[{\"inherited\":true}]}]}");
        respostas.add("{\"permissions\":[{\"id\":\"ana\",\"type\":\"user\",\"role\":\"writer\",\"emailAddress\":\"ana@climbe.com.br\",\"permissionDetails\":[{\"inherited\":true},{\"inherited\":false}]}]}");
        var permissoes = client().listar("cfo");
        assertThat(permissoes).hasSize(2);
        assertThat(permissoes.getFirst().herdada()).isTrue(); assertThat(permissoes.getFirst().metadados()).isTrue();
        assertThat(permissoes.getLast().herdada()).isFalse();
        assertThat(pedidos.getLast().getUrl()).contains("pageToken=proxima", "supportsAllDrives=true");
    }
    @Test void compartilhaComPessoaSemConvitePorEmailERemoveComRespostaSemCorpo() throws Exception {
        respostas.add("{\"id\":\"ana\"}");
        var client = client(); client.conceder("cfo", "ana@climbe.com.br", "writer"); client.remover("cfo", "ana");
        assertThat(pedidos.getFirst().getContentAsString()).contains("\"type\":\"user\"", "ana@climbe.com.br", "\"role\":\"writer\"");
        assertThat(pedidos.getFirst().getUrl()).contains("sendNotificationEmail=false");
        assertThat(metodos).containsExactly("POST", "DELETE");
    }
}
