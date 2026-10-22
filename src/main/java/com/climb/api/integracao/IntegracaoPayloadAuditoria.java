package com.climb.api.integracao;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.util.ContentCachingRequestWrapper;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

final class IntegracaoPayloadAuditoria {
    static final int LIMITE_BYTES = 64 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(LIMITE_BYTES).build()).build());
    private static final Set<String> SENSIVEIS = Set.of("secret", "token", "password", "senha", "authorization",
            "apikey", "privatekey", "credential", "credencial", "cookie", "sessionid");
    record Captura(String payload, boolean limiteExcedido) {}

    private IntegracaoPayloadAuditoria() {}

    static Captura capturar(ContentCachingRequestWrapper request, int status) {
        if (!"POST".equals(request.getMethod()) || status != 201) return new Captura(null, false);
        byte[] corpo = request.getContentAsByteArray();
        if (request.getContentLengthLong() > LIMITE_BYTES || corpo.length > LIMITE_BYTES)
            return new Captura(null, true);
        if (corpo.length == 0 || request.getContentType() == null) return new Captura(null, false);
        try {
            if (!MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType())))
                return new Captura(null, false);
            JsonNode conteudo = JSON.readTree(corpo);
            if (conteudo == null) return new Captura(null, false);
            ocultarCredenciais(conteudo);
            return new Captura(JSON.writeValueAsString(conteudo), false);
        } catch (IOException | IllegalArgumentException e) {
            return new Captura(null, false);
        }
    }

    private static void ocultarCredenciais(JsonNode valor) {
        if (valor.isObject()) {
            ObjectNode objeto = (ObjectNode) valor;
            objeto.properties().forEach(campo -> {
                String chave = campo.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (SENSIVEIS.stream().anyMatch(chave::contains)) objeto.put(campo.getKey(), "[OCULTO]");
                else ocultarCredenciais(campo.getValue());
            });
        } else if (valor.isArray()) valor.forEach(IntegracaoPayloadAuditoria::ocultarCredenciais);
    }
}
