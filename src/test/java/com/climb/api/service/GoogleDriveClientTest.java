package com.climb.api.service;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleDriveClientTest {
    private final List<MockLowLevelHttpRequest> pedidos = new ArrayList<>();
    private final List<String> metodos = new ArrayList<>();
    private final Queue<String> respostas = new ArrayDeque<>();
    private final MockHttpTransport transporte = new MockHttpTransport() {
        @Override public boolean supportsMethod(String metodo) { return !"PATCH".equals(metodo); }
        @Override public LowLevelHttpRequest buildRequest(String metodo, String url) {
            if (!supportsMethod(metodo)) throw new IllegalArgumentException("Método não suportado: " + metodo);
            metodos.add(metodo);
            var pedido = new MockLowLevelHttpRequest(url).setResponse(new MockLowLevelHttpResponse()
                    .setStatusCode(200).setContentType("application/json").setContent(respostas.remove()));
            pedidos.add(pedido); return pedido;
        }
    };

    private GoogleDriveClient client() {
        var credentials = mock(GoogleDriveCredentialService.class); when(credentials.obterAccessToken()).thenReturn("token-de-teste");
        return new GoogleDriveClient(credentials, transporte);
    }

    @Test void renomeiaPastaExistenteComTransporteQueNaoAceitaPatchSemCriarDuplicata() throws Exception {
        respostas.add("{\"files\":[{\"id\":\"empresa\",\"name\":\"Nome antigo\",\"mimeType\":\"application/vnd.google-apps.folder\"}]}");
        respostas.add("{\"id\":\"empresa\"}");
        assertThat(client().garantirPasta("raiz", "Empresa-37.335.118/0001-80", "empresa-1")).isEqualTo("empresa");
        assertThat(metodos).containsExactly("GET", "POST");
        assertThat(pedidos.getLast().getFirstHeaderValue("X-HTTP-Method-Override")).isEqualTo("PATCH");
        assertThat(pedidos.getLast().getContentAsString()).contains("Empresa-37.335.118/0001-80");
        assertThat(pedidos.getFirst().getFirstHeaderValue("Authorization")).isEqualTo("Bearer token-de-teste");
    }

    @Test void listaTodasAsPaginasIncluindoPaginaInicialVazia() {
        respostas.add("{\"files\":[],\"nextPageToken\":\"proxima\"}");
        respostas.add("{\"files\":[{\"id\":\"pdf\",\"name\":\"Balanço.pdf\",\"mimeType\":\"application/pdf\",\"size\":\"123\",\"parents\":[\"servico\"]}]}");
        var arquivos = client().listar("servico");
        assertThat(arquivos).hasSize(1); assertThat(arquivos.getFirst().tamanho()).isEqualTo(123);
        var url = new GenericUrl(pedidos.getLast().getUrl());
        assertThat(url.get("pageToken")).isEqualTo(List.of("proxima"));
        assertThat(url.get("supportsAllDrives")).isEqualTo(List.of("true"));
        assertThat(url.get("includeItemsFromAllDrives")).isEqualTo(List.of("true"));
    }

    @Test void atualizaPdfIdentificadoPelaChaveSemDuplicarEUsaUploadMultipartRelated() throws Exception {
        respostas.add("{\"files\":[{\"id\":\"pdf\",\"name\":\"RFB.pdf\",\"mimeType\":\"application/pdf\"}]}");
        respostas.add("{\"id\":\"pdf\",\"name\":\"RFB.pdf\",\"mimeType\":\"application/pdf\"}");
        assertThat(client().enviar("documentacoes", "RFB.pdf", "application/pdf", "%PDF-1.4".getBytes(), "comprovante-1").id()).isEqualTo("pdf");
        assertThat(pedidos.getLast().getContentType()).startsWith("multipart/related");
        assertThat(pedidos.getLast().getUrl()).contains("/upload/drive/v3/files/pdf", "uploadType=multipart");
        assertThat(pedidos.getLast().getContentAsString()).contains("%PDF-1.4", "comprovante-1").doesNotContain("\"parents\"");
    }
}
