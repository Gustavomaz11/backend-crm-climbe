package com.climb.api.service;

import com.climb.api.config.ZapSignProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZapSignClient {
    private static final Logger log = LoggerFactory.getLogger(ZapSignClient.class);

    private final ZapSignProperties properties;
    private final RestClient restClient;

    @Autowired
    public ZapSignClient(ZapSignProperties properties) {
        this(properties, criarRestClient(properties));
    }

    ZapSignClient(ZapSignProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient criarRestClient(ZapSignProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(properties.getApiUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public Documento criarDocumento(String nomeDocumento,
                                     byte[] pdf,
                                     String nomeSignatario,
                                     String emailSignatario,
                                     String cpfSignatario,
                                     String redirectLink,
                                     String externalId) {
        validarConfiguracao();

        Map<String, Object> signatario = new LinkedHashMap<>();
        signatario.put("name", nomeSignatario);
        signatario.put("email", emailSignatario);
        signatario.put("auth_mode", "assinaturaTela-tokenEmail");
        signatario.put("send_automatic_email", false);
        signatario.put("lock_email", true);
        signatario.put("redirect_link", redirectLink);
        signatario.put("external_id", externalId + "-signatario");
        if (StringUtils.hasText(cpfSignatario)) {
            signatario.put("cpf", somenteDigitos(cpfSignatario));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", nomeDocumento);
        body.put("base64_pdf", Base64.getEncoder().encodeToString(pdf));
        body.put("lang", "pt-br");
        body.put("external_id", externalId);
        body.put("signers", List.of(signatario));

        try {
            DocumentoResponse response = restClient.post()
                    .uri("/api/v1/docs/")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiToken().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(DocumentoResponse.class);
            return validarDocumento(response);
        } catch (RestClientResponseException exception) {
            throw traduzirRespostaErro("criar documento", exception);
        } catch (RestClientException exception) {
            log.warn("Falha ao criar documento na ZapSign: {}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível iniciar a assinatura do contrato na ZapSign. Tente novamente", exception);
        }
    }

    public Documento detalharDocumento(String documentoToken) {
        validarConfiguracao();
        try {
            DocumentoResponse response = restClient.get()
                    .uri("/api/v1/docs/{token}/", documentoToken)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiToken().trim())
                    .retrieve()
                    .body(DocumentoResponse.class);
            return validarDocumento(response);
        } catch (RestClientResponseException exception) {
            throw traduzirRespostaErro("consultar documento", exception);
        } catch (RestClientException exception) {
            log.warn("Falha ao consultar documento na ZapSign: {}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar a assinatura do contrato na ZapSign", exception);
        }
    }

    public String montarUrlAssinatura(String signatarioToken) {
        return properties.getSigningUrl().replaceAll("/+$", "") + "/" + signatarioToken;
    }

    private Documento validarDocumento(DocumentoResponse response) {
        if (response == null || !StringUtils.hasText(response.token())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A ZapSign retornou uma resposta inválida para o documento");
        }
        List<Signatario> signatarios = response.signers() == null
                ? List.of()
                : response.signers().stream()
                        .map(item -> new Signatario(item.token(), item.status()))
                        .toList();
        return new Documento(response.token(), response.status(), signatarios);
    }

    private void validarConfiguracao() {
        if (!StringUtils.hasText(properties.getApiToken())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A integração com a ZapSign não está configurada");
        }
    }

    private ResponseStatusException traduzirRespostaErro(String operacao,
                                                          RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        log.warn("ZapSign recusou a operação '{}': HTTP {}", operacao, status);

        String mensagem = switch (status) {
            case 401, 403 -> "A credencial da ZapSign foi recusada ou não possui permissão para criar documentos";
            case 400, 422 -> "A ZapSign recusou os dados enviados para assinatura do contrato";
            case 413 -> "O PDF excede o tamanho máximo aceito pela ZapSign";
            case 429 -> "O limite de requisições da ZapSign foi atingido. Tente novamente em instantes";
            default -> "A ZapSign está temporariamente indisponível. Tente novamente";
        };
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem, exception);
    }

    private String somenteDigitos(String value) {
        return value.replaceAll("\\D", "");
    }

    public record Documento(String token, String status, List<Signatario> signatarios) {
        public String primeiroSignatarioToken() {
            return signatarios == null ? null : signatarios.stream()
                    .map(Signatario::token)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
        }

        public boolean assinado() {
            return "signed".equalsIgnoreCase(status);
        }
    }

    public record Signatario(String token, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DocumentoResponse(String token, String status, List<SignatarioResponse> signers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SignatarioResponse(String token, String status) {}
}
