package com.climb.api.service;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.googleapis.MethodOverride;
import com.google.gson.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;

@Service
public class GoogleDriveClient {
    public static final String PASTA = "application/vnd.google-apps.folder";
    private static final String API = "https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String CAMPOS = "id,name,mimeType,size,parents,trashed,webViewLink";
    private final GoogleDriveCredentialService credentials;
    private final HttpTransport transport;

    public record Arquivo(String id, String nome, String contentType, long tamanho, List<String> pais,
            boolean removido, String webViewLink) {
        public boolean pasta() { return PASTA.equals(contentType); }
    }
    public record Conteudo(String nome, String contentType, byte[] bytes) {}

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleDriveClient(GoogleDriveCredentialService credentials) { this(credentials, new NetHttpTransport()); }

    GoogleDriveClient(GoogleDriveCredentialService credentials, HttpTransport transport) {
        this.credentials = credentials; this.transport = transport;
    }

    public Arquivo buscar(String id) {
        return arquivo(json("GET", url(API + "/" + id).set("fields", CAMPOS), null));
    }

    public List<Arquivo> listar(String pastaId) {
        return pesquisar("'" + escapar(pastaId) + "' in parents and trashed = false");
    }

    public String garantirPasta(String pai, String nome, String chave) {
        String filtro = "'" + escapar(pai) + "' in parents and trashed = false and mimeType = '" + PASTA + "'";
        var existentes = pesquisar(filtro + " and appProperties has { key='climbe' and value='" + escapar(chave) + "' }");
        if (!existentes.isEmpty()) {
            var existente = existentes.getFirst();
            if (!nome.equals(existente.nome())) json("PATCH", url(API + "/" + existente.id()), metadata(Map.of("name", nome)));
            return existente.id();
        }
        existentes = pesquisar(filtro + " and name = '" + escapar(nome) + "'");
        if (!existentes.isEmpty()) {
            String id = existentes.getFirst().id();
            json("PATCH", url(API + "/" + id), metadata(Map.of("appProperties", Map.of("climbe", chave))));
            return id;
        }
        return criarPasta(pai, nome, chave).id();
    }

    public Arquivo criarPasta(String pai, String nome, String chave) {
        var dados = new HashMap<String, Object>();
        dados.put("name", nome); dados.put("mimeType", PASTA); dados.put("parents", List.of(pai));
        if (chave != null) dados.put("appProperties", Map.of("climbe", chave));
        return arquivo(json("POST", url(API).set("fields", CAMPOS), metadata(dados)));
    }

    public Arquivo enviar(String pai, String nome, String tipo, byte[] bytes, String chave) {
        String id = null;
        if (chave != null) {
            var existentes = pesquisar("'" + escapar(pai) + "' in parents and trashed = false and appProperties has { key='climbe' and value='" + escapar(chave) + "' }");
            if (!existentes.isEmpty()) id = existentes.getFirst().id();
        }
        var dados = new HashMap<String, Object>(); dados.put("name", nome);
        if (id == null) dados.put("parents", List.of(pai));
        if (chave != null) dados.put("appProperties", Map.of("climbe", chave));
        var multipart = new MultipartContent()
                .addPart(new MultipartContent.Part(metadata(dados)))
                .addPart(new MultipartContent.Part(new ByteArrayContent(tipo, bytes)));
        return arquivo(json(id == null ? "POST" : "PATCH", url(UPLOAD + (id == null ? "" : "/" + id))
                .set("uploadType", "multipart").set("fields", CAMPOS), multipart));
    }

    public Conteudo baixar(Arquivo item) {
        if (item.pasta()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo.");
        String tipo = item.contentType(); String nome = item.nome();
        GenericUrl endereco = url(API + "/" + item.id()).set("alt", "media");
        if (tipo.startsWith("application/vnd.google-apps.")) {
            tipo = switch (tipo) {
                case "application/vnd.google-apps.document" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "application/vnd.google-apps.spreadsheet" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "application/vnd.google-apps.presentation" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                default -> "application/pdf";
            };
            nome += tipo.endsWith("document") ? ".docx" : tipo.endsWith("sheet") ? ".xlsx" : tipo.endsWith("presentation") ? ".pptx" : ".pdf";
            endereco = new GenericUrl(API + "/" + item.id() + "/export").set("mimeType", tipo);
        }
        HttpResponse resposta = executar("GET", endereco, null);
        try { return new Conteudo(nome, tipo, resposta.getContent().readAllBytes()); }
        catch (IOException exception) { throw falha(); }
        finally { desconectar(resposta); }
    }

    private List<Arquivo> pesquisar(String filtro) {
        var itens = new ArrayList<Arquivo>(); String pagina = null;
        do {
            var endereco = url(API).set("q", filtro).set("pageSize", 1000).set("includeItemsFromAllDrives", true)
                    .set("fields", "nextPageToken,files(" + CAMPOS + ")").set("orderBy", "folder,name");
            if (pagina != null) endereco.set("pageToken", pagina);
            var dados = json("GET", endereco, null);
            if (dados.has("files")) dados.getAsJsonArray("files").forEach(item -> itens.add(arquivo(item.getAsJsonObject())));
            pagina = valor(dados, "nextPageToken");
        } while (pagina != null);
        return itens;
    }

    private JsonObject json(String metodo, GenericUrl endereco, HttpContent corpo) {
        HttpResponse resposta = executar(metodo, endereco, corpo);
        try { return JsonParser.parseString(resposta.parseAsString()).getAsJsonObject(); }
        catch (IOException exception) { throw falha(); }
        finally { desconectar(resposta); }
    }

    private HttpResponse executar(String metodo, GenericUrl endereco, HttpContent corpo) {
        String token = credentials.obterAccessToken();
        try {
            return transport.createRequestFactory(request -> {
                request.getHeaders().setAuthorization("Bearer " + token);
                new MethodOverride().initialize(request);
                request.setConnectTimeout(10000); request.setReadTimeout(60000);
                request.setNumberOfRetries(0);
            }).buildRequest(metodo, endereco, corpo).execute();
        } catch (HttpResponseException exception) {
            if (exception.getStatusCode() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo ou pasta não encontrado no Drive.");
            throw falha();
        } catch (IOException exception) { throw falha(); }
    }

    private Arquivo arquivo(JsonObject dados) {
        var pais = new ArrayList<String>();
        if (dados.has("parents")) dados.getAsJsonArray("parents").forEach(pai -> pais.add(pai.getAsString()));
        return new Arquivo(valor(dados, "id"), valor(dados, "name"), valor(dados, "mimeType"),
                dados.has("size") ? dados.get("size").getAsLong() : 0, pais,
                dados.has("trashed") && dados.get("trashed").getAsBoolean(), valor(dados, "webViewLink"));
    }
    private GenericUrl url(String endereco) { return new GenericUrl(endereco).set("supportsAllDrives", true); }
    private JsonHttpContent metadata(Map<String, ?> dados) { return new JsonHttpContent(GsonFactory.getDefaultInstance(), dados); }
    private String valor(JsonObject dados, String chave) { return dados.has(chave) && !dados.get(chave).isJsonNull() ? dados.get(chave).getAsString() : null; }
    private String escapar(String valor) { return valor.replace("\\", "\\\\").replace("'", "\\'"); }
    private void desconectar(HttpResponse resposta) { try { resposta.disconnect(); } catch (IOException ignored) { } }
    private ResponseStatusException falha() { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível acessar o Drive. Verifique a autorização e o acesso à pasta."); }
}
