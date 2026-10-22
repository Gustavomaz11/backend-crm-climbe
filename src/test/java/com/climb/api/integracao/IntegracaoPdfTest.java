package com.climb.api.integracao;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IntegracaoPdfTest {
    @Test void documentaSomenteOperacoesAutorizadasEProduzPdfLegivel() throws Exception {
        var catalogo = new IntegracaoCatalogo(); var pdf = new IntegracaoPdfService(catalogo);
        ReflectionTestUtils.setField(pdf, "apiUrl", "https://api.example.com");
        var c = new IntegracaoCliente(); c.setNome("Empresa de Integração e Serviços"); c.setClientId("climbe_exemplo");
        c.setModulo(IntegracaoModulo.JURIDICO); c.setMetodos(Set.of("GET", "PATCH"));
        c.setOperacoes(Set.of("juridico.contratos.ler", "juridico.contratos.aprovar")); c.setEmpresaIds(Set.of(12L)); c.setExpiraEm(Instant.parse("2027-12-31T23:59:59Z"));
        byte[] bytes = pdf.gerar(c);
        var dir = Path.of("target", "integracao-preview"); Files.createDirectories(dir); Files.write(dir.resolve("documentacao-exemplo.pdf"), bytes);
        Files.writeString(dir.resolve("catalogo.json"), new com.google.gson.Gson().toJson(catalogo.listar(null)));
        try (var doc = Loader.loadPDF(bytes)) {
            var texto = new PDFTextStripper().getText(doc);
            assertThat(texto).contains("/api/integracoes/oauth/token", "PATCH", "/contratos/{id}/aprovar", "APROVADO", "CLIENT_SECRET");
            assertThat(texto).contains("Empresas autorizadas para GET", "consultas GET", "seleção prévia");
            assertThat(texto).doesNotContain("DELETE", "/comercial/", "/tecnico/");
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            var renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) ImageIO.write(renderer.renderImageWithDPI(i, 100), "png", dir.resolve("pagina-" + (i + 1) + ".png").toFile());
        }
    }
    @Test void pdfSemGetNaoExibeSelecaoDeEmpresas() throws Exception {
        var pdf = new IntegracaoPdfService(new IntegracaoCatalogo()); ReflectionTestUtils.setField(pdf, "apiUrl", "https://api.example.com");
        var c = new IntegracaoCliente(); c.setNome("Plataforma exemplo"); c.setClientId("climbe_exemplo"); c.setModulo(IntegracaoModulo.COMERCIAL);
        c.setMetodos(Set.of("POST")); c.setOperacoes(Set.of("comercial.propostas.criar")); c.setEmpresaIds(Set.of()); c.setExpiraEm(Instant.parse("2027-12-31T23:59:59Z"));
        try (var doc = Loader.loadPDF(pdf.gerar(c))) {
            var texto = new PDFTextStripper().getText(doc);
            assertThat(texto).doesNotContain("Empresas autorizadas", "consultas GET");
            assertThat(texto).contains("seleção prévia", "comercial.propostas.criar");
        }
    }
    @Test void documentaCriacaoDoLeadComDestinoAutomaticoEmPreVendas() throws Exception {
        var pdf = new IntegracaoPdfService(new IntegracaoCatalogo());
        ReflectionTestUtils.setField(pdf, "apiUrl", "https://climbe-api-ewke.onrender.com");
        var c = new IntegracaoCliente(); c.setNome("Plataforma de leads"); c.setClientId("climbe_exemplo");
        c.setModulo(IntegracaoModulo.COMERCIAL); c.setMetodos(Set.of("POST"));
        c.setOperacoes(Set.of("comercial.prevendas.leads.criar")); c.setEmpresaIds(Set.of());
        c.setExpiraEm(Instant.parse("2027-12-31T23:59:59Z"));
        byte[] bytes = pdf.gerar(c);
        var dir = Path.of("target", "integracao-preview"); Files.createDirectories(dir);
        Files.write(dir.resolve("documentacao-prevendas.pdf"), bytes);
        try (var doc = Loader.loadPDF(bytes)) {
            String texto = new PDFTextStripper().getText(doc);
            assertThat(texto).contains("POST /api/integracoes/v1/comercial/pre-vendas/leads", "comercial.prevendas.leads.criar",
                    "Lista de leads", "Todas as estratégias", "O destino é automático", "empresaId", "nomeContato");
            assertThat(texto).doesNotContain("/comercial/negocios", "/comercial/propostas");
            int inicio = texto.lastIndexOf("--data '") + "--data '".length();
            var payload = com.google.gson.JsonParser.parseString(texto.substring(inicio, texto.indexOf("'", inicio))).getAsJsonObject();
            assertThat(payload.get("email").getAsString()).isEqualTo("contato@example.com");
            assertThat(payload.has("funilId") || payload.has("etapaId") || payload.has("empresaId")).isFalse();
            assertThat(payload.get("responsavelId").getAsInt()).isZero();
            assertThat(payload.get("nomeEmpresa").getAsString()).isNotBlank();
            assertThat(payload.get("cnpj").getAsString()).isNotBlank();
        }
    }
    @Test void todaRotaExternaTemEntradaNoCatalogoSemPermissoesImplicitas() {
        var catalogo = new IntegracaoCatalogo(); int rotas = 0;
        for (var tipo : List.of(IntegracaoTecnicoController.class, IntegracaoComercialController.class, IntegracaoJuridicoController.class)) {
            for (var metodo : tipo.getDeclaredMethods()) {
                var mapping = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
                if (mapping == null) continue; rotas++;
                var anotacao = metodo.getAnnotation(IntegracaoEndpoint.class); assertThat(anotacao).isNotNull();
                var item = catalogo.listar(null).stream().filter(o -> o.codigo().equals(anotacao.codigo())).findFirst().orElseThrow();
                assertThat(item.respostaExemplo()).isNotBlank();
                assertThat(catalogo.localizar(item.metodo(), item.caminho().replaceAll("\\{[^}]+}", "1"))).contains(item);
                if (Set.of("POST", "PUT", "PATCH").contains(item.metodo())) assertThat(item.payloadExemplo()).isNotBlank();
            }
        }
        assertThat(catalogo.listar(null)).hasSize(rotas);
    }
}
