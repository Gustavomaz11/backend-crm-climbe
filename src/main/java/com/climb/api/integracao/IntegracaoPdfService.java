package com.climb.api.integracao;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class IntegracaoPdfService {
    private final IntegracaoCatalogo catalogo;
    @Value("${app.integracoes.api-url:http://localhost:8080}") private String apiUrl;

    public byte[] gerar(IntegracaoCliente cliente) {
        try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream(); var texto = new Paginas(documento)) {
            documento.getDocumentInformation().setTitle("Climbe | Documentação da integração " + cliente.getNome());
            texto.titulo("Climbe - API de integração");
            texto.linha("Empresa/plataforma: " + cliente.getNome());
            texto.linha("Client ID: " + cliente.getClientId());
            texto.linha("Módulo: " + cliente.getModulo() + " | Métodos: " + String.join(", ", new TreeSet<>(cliente.getMetodos())));
            if (cliente.getMetodos().contains("GET")) texto.linha("Empresas autorizadas para GET (IDs): " + new TreeSet<>(cliente.getEmpresaIds()));
            texto.linha("Validade: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z").withZone(ZoneId.of("America/Sao_Paulo")).format(cliente.getExpiraEm()));
            texto.linha("Versão da API: v1 | Documento gerado em " + LocalDate.now(ZoneId.of("America/Sao_Paulo")));
            texto.linha("URL base: " + apiUrl.replaceAll("/+$", ""));
            texto.titulo("Autenticação");
            texto.linha("Ative a integração pelo link enviado por e-mail. O client_secret será exibido uma única vez. Nunca inclua credenciais em URLs ou código de navegador. Em produção, use HTTPS.");
            texto.linha("POST /api/integracoes/oauth/token");
            texto.linha("Content-Type: application/x-www-form-urlencoded");
            texto.linha("Authorization: Basic base64(CLIENT_ID:CLIENT_SECRET)");
            texto.codigo("grant_type=client_credentials");
            texto.linha("Exemplo de requisição:");
            texto.codigo("curl -X POST '" + apiUrl.replaceAll("/+$", "") + "/api/integracoes/oauth/token' \\\n  -u 'CLIENT_ID:CLIENT_SECRET' \\\n  -H 'Content-Type: application/x-www-form-urlencoded' \\\n  --data 'grant_type=client_credentials'");
            texto.json("{\"access_token\":\"TOKEN_EXEMPLO\",\"token_type\":\"Bearer\",\"expires_in\":900,\"scope\":\"OPERACOES_AUTORIZADAS\"}");
            texto.linha("Envie Authorization: Bearer ACCESS_TOKEN nas operações abaixo. O token dura até 15 minutos, limitado à validade da integração. Obtenha outro token usando as credenciais quando expirar; não há refresh token.");
            texto.titulo("Regras de acesso e erros");
            texto.linha("Acesso limitado ao módulo, métodos e operações deste cadastro. As regras de negócio e permissões do usuário que criou a integração continuam aplicáveis. Novos endpoints exigem liberação explícita.");
            texto.linha("Limite compartilhado: " + cliente.getLimitePorMinuto() + " requisições/minuto por integração, incluindo emissão de tokens. Respostas 429 incluem Retry-After. Paginação começa em zero e aceita tamanho entre 1 e 100.");
            texto.linha("HTTP 400: dados inválidos; 401: credencial inválida, expirada ou revogada; 403: operação sem permissão; 404: registro inexistente ou fora do escopo; 409: conflito; 429: limite atingido. Verifique o campo message da resposta.");
            texto.linha("IDs e valores abaixo são fictícios. Substitua-os pelos registros autorizados. Não repita gravações automaticamente sem verificar o resultado anterior.");
            for (var operacao : catalogo.permitidas(cliente)) {
                texto.novaPagina();
                texto.titulo(operacao.metodo() + " " + operacao.caminho());
                texto.linha("Operação: " + operacao.codigo());
                texto.linha(operacao.descricao());
                texto.linha("GET".equals(operacao.metodo())
                        ? "As consultas GET abrangem somente as empresas autorizadas listadas neste documento."
                        : "A empresa é definida na requisição ou pelo registro alterado, sem seleção prévia no cadastro da integração. Os vínculos entre registros continuam sendo validados.");
                texto.linha("Header: Authorization: Bearer ACCESS_TOKEN");
                texto.linha("Parâmetros de caminho: substitua cada {id}, {tarefaId} ou outro identificador pelo ID do registro autorizado.");
                if (!operacao.payloadExemplo().isBlank()) {
                    texto.linha("Content-Type: application/json"); texto.titulo("Payload de exemplo"); texto.json(operacao.payloadExemplo());
                } else texto.linha("Esta operação não recebe corpo JSON.");
                texto.titulo("Exemplo em cURL");
                String payloadCurl = operacao.payloadExemplo().isBlank() ? "" : new com.google.gson.GsonBuilder()
                        .setPrettyPrinting().create().toJson(com.google.gson.JsonParser.parseString(operacao.payloadExemplo()));
                texto.codigo("curl -X " + operacao.metodo() + " '" + apiUrl.replaceAll("/+$", "") + operacao.caminho()
                        + "' \\\n  -H 'Authorization: Bearer ACCESS_TOKEN'" + (operacao.payloadExemplo().isBlank() ? ""
                        : " \\\n  -H 'Content-Type: application/json' \\\n  --data '" + payloadCurl + "'"));
                texto.titulo("Resposta de exemplo");
                if (operacao.respostaExemplo().startsWith("{")) texto.json(operacao.respostaExemplo()); else texto.linha(operacao.respostaExemplo());
            }
            texto.fecharPagina(); documento.save(saida); return saida.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Não foi possível gerar a documentação PDF.", e); }
    }

    private static final class Paginas implements AutoCloseable {
        private final PDDocument documento;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDFont codigo = new PDType1Font(Standard14Fonts.FontName.COURIER);
        private PDPageContentStream pagina;
        private float y;
        private int numero;
        Paginas(PDDocument documento) throws IOException { this.documento = documento; novaPagina(); }
        void novaPagina() throws IOException {
            fecharPagina(); var page = new PDPage(PDRectangle.A4); documento.addPage(page);
            pagina = new PDPageContentStream(documento, page); y = 785; numero++;
            escrever("CLIMBE  /  INTEGRAÇÕES  /  API v1", regular, 8, 815);
        }
        void titulo(String valor) throws IOException { y -= 8; paragrafo(valor, negrito, 13, 19); y -= 6; }
        void linha(String valor) throws IOException { paragrafo(valor, regular, 10, 15); y -= 6; }
        void codigo(String valor) throws IOException { paragrafo(valor, codigo, 8, 12); y -= 8; }
        void json(String valor) throws IOException {
            codigo(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(com.google.gson.JsonParser.parseString(valor)));
        }
        void paragrafo(String valor, PDFont fonte, float tamanho, float altura) throws IOException {
            for (String original : limpar(valor, fonte).split("\n", -1)) {
                String restante = original;
                while (!restante.isEmpty()) {
                    int limite = restante.length();
                    while (limite > 1 && fonte.getStringWidth(restante.substring(0, limite)) * tamanho / 1000 > 495) limite--;
                    if (limite < restante.length() && fonte != codigo) {
                        int espaco = restante.lastIndexOf(' ', limite);
                        if (espaco > 0) limite = espaco;
                    }
                    if (y < 65) novaPagina(); escrever(restante.substring(0, limite), fonte, tamanho, y); y -= altura;
                    restante = restante.substring(limite);
                    if (fonte != codigo) restante = restante.stripLeading();
                }
                if (original.isEmpty()) y -= altura;
            }
        }
        String limpar(String valor, PDFont fonte) {
            var result = new StringBuilder();
            for (char c : valor.toCharArray()) {
                if (c == '\n') { result.append(c); continue; }
                if (Character.isISOControl(c)) { result.append(' '); continue; }
                try { fonte.encode(String.valueOf(c)); result.append(c); }
                catch (Exception e) { result.append('?'); }
            }
            return result.toString();
        }
        void escrever(String valor, PDFont fonte, float tamanho, float posicao) throws IOException {
            pagina.beginText(); pagina.setFont(fonte, tamanho); pagina.newLineAtOffset(50, posicao); pagina.showText(valor); pagina.endText();
        }
        void fecharPagina() throws IOException {
            if (pagina == null) return;
            escrever("Climbe | Documentação exclusiva da integração | Página " + numero, regular, 8, 35);
            pagina.close(); pagina = null;
        }
        @Override public void close() throws IOException { fecharPagina(); }
    }
}
