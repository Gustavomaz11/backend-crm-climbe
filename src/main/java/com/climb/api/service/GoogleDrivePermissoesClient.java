package com.climb.api.service;

import com.google.gson.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class GoogleDrivePermissoesClient {
    private static final String API = "https://www.googleapis.com/drive/v3/files/";
    private final GoogleDriveClient drive;
    public GoogleDrivePermissoesClient(GoogleDriveClient drive) { this.drive = drive; }

    public record Permissao(String id, String tipo, String papel, String email, boolean herdada, boolean metadados) {
        public boolean administrativa() { return "owner".equals(papel) || "organizer".equals(papel); }
    }
    public record Identidade(String id, String email) {}

    public Identidade identidade() {
        var user = drive.json("GET", new com.google.api.client.http.GenericUrl("https://www.googleapis.com/drive/v3/about")
                .set("fields", "user(permissionId,emailAddress)"), null).getAsJsonObject("user");
        return new Identidade(texto(user, "permissionId"), texto(user, "emailAddress"));
    }

    public void limitar(String pastaId, boolean limitado) {
        var dados = drive.json("GET", drive.url(API + pastaId).set("fields",
                "id,mimeType,driveId,inheritedPermissionsDisabled,capabilities(canDisableInheritedPermissions,canEnableInheritedPermissions,canShare)"), null);
        if (!GoogleDriveClient.PASTA.equals(texto(dados, "mimeType"))) throw falha("O item do serviço precisa ser uma pasta.");
        boolean atual = booleano(dados, "inheritedPermissionsDisabled");
        var capacidades = dados.getAsJsonObject("capabilities");
        if (atual != limitado && !booleano(capacidades, limitado ? "canDisableInheritedPermissions" : "canEnableInheritedPermissions"))
            throw falha("A conta da integração não pode alterar o acesso limitado desta pasta.");
        if (!booleano(capacidades, "canShare")) throw falha("A conta da integração não pode gerenciar o compartilhamento desta pasta.");
        var alteracoes = new HashMap<String, Object>();
        if (atual != limitado) alteracoes.put("inheritedPermissionsDisabled", limitado);
        if (limitado && !dados.has("driveId")) alteracoes.put("writersCanShare", false);
        if (!alteracoes.isEmpty()) drive.json("PATCH", drive.url(API + pastaId).set("fields", "id"), drive.metadata(alteracoes));
    }

    public List<Permissao> listar(String id) {
        var permissoes = new ArrayList<Permissao>(); String pagina = null;
        do {
            var url = drive.url(API + id + "/permissions").set("pageSize", 100)
                    .set("fields", "nextPageToken,permissions(id,type,role,emailAddress,view,permissionDetails(inherited))");
            if (pagina != null) url.set("pageToken", pagina);
            var dados = drive.json("GET", url, null);
            if (dados.has("permissions")) dados.getAsJsonArray("permissions").forEach(item -> {
                var p = item.getAsJsonObject();
                boolean herdada = p.has("permissionDetails") && !p.getAsJsonArray("permissionDetails").isEmpty();
                if (herdada) for (var detalhe : p.getAsJsonArray("permissionDetails"))
                    herdada &= booleano(detalhe.getAsJsonObject(), "inherited");
                permissoes.add(new Permissao(texto(p, "id"), texto(p, "type"), texto(p, "role"),
                        texto(p, "emailAddress"), herdada, "metadata".equals(texto(p, "view"))));
            });
            pagina = texto(dados, "nextPageToken");
        } while (pagina != null);
        return permissoes;
    }

    public void conceder(String id, String email, String papel) {
        drive.json("POST", drive.url(API + id + "/permissions").set("sendNotificationEmail", false).set("fields", "id"),
                drive.metadata(Map.of("type", "user", "emailAddress", email, "role", papel)));
    }
    public void alterar(String id, String permissaoId, String papel) {
        drive.json("PATCH", drive.url(API + id + "/permissions/" + permissaoId).set("fields", "id"), drive.metadata(Map.of("role", papel)));
    }
    public void remover(String id, String permissaoId) {
        try {
            var resposta = drive.executar("DELETE", drive.url(API + id + "/permissions/" + permissaoId), null);
            drive.desconectar(resposta);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 404) throw exception;
        }
    }
    private static String texto(JsonObject dados, String chave) {
        return dados == null || !dados.has(chave) || dados.get(chave).isJsonNull() ? null : dados.get(chave).getAsString();
    }
    private static boolean booleano(JsonObject dados, String chave) {
        return dados != null && dados.has(chave) && !dados.get(chave).isJsonNull() && dados.get(chave).getAsBoolean();
    }
    private ResponseStatusException falha(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem); }
}
