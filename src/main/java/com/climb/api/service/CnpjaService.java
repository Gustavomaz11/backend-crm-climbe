package com.climb.api.service;

import com.climb.api.model.dto.CnpjEmpresaResponseDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.br.CNPJ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.core.ParameterizedTypeReference;

@Service
public class CnpjaService {
    private final String apiKey;
    private final Validator validator;
    private final RestClient client;

    @Autowired
    public CnpjaService(@Value("${app.cnpja.api-key:}") String apiKey, Validator validator) {
        this(apiKey, validator, criarCliente());
    }

    CnpjaService(String apiKey, Validator validator, RestClient.Builder builder) {
        this.apiKey = texto(apiKey);
        this.validator = validator;
        this.client = builder.baseUrl("https://api.cnpja.com").build();
    }

    private static RestClient.Builder criarCliente() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory);
    }

    public CnpjEmpresaResponseDTO consultar(String cnpj) {
        if (!validator.validate(new Consulta(cnpj)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um CNPJ válido.");
        }
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A consulta CNPJá não está configurada. Preencha os dados manualmente.");
        }
        try {
            var office = client.get().uri("/office/{cnpj}", cnpj)
                    .header(HttpHeaders.AUTHORIZATION, apiKey).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (office == null || texto(CnpjaDadosCadastrais.objeto(office.get("company")).get("name")).isBlank()
                    || !cnpj.equals(office.get("taxId"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O CNPJá retornou dados cadastrais incompletos.");
            }
            return converter(office, consultarFontes(cnpj, office));
        } catch (RestClientResponseException exception) {
            throw erroConsulta(exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar o CNPJ. Preencha os dados manualmente ou tente novamente.");
        }
    }

    private List<String> consultarFontes(String cnpj, Map<String, Object> office) {
        var avisos = new ArrayList<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var simples = executor.submit(() -> consultarFonte("/simples?taxId={cnpj}&history=false", cnpj, "Simples Nacional e MEI"));
            var ccc = executor.submit(() -> consultarFonte("/ccc?taxId={cnpj}&states=ALL&source=CCC", cnpj, "Cadastro de Contribuintes"));
            var sintegra = executor.submit(() -> consultarFonte("/ccc?taxId={cnpj}&states=ALL&source=SINTEGRA", cnpj, "SINTEGRA"));
            var suframa = executor.submit(() -> consultarFonte("/suframa?taxId={cnpj}", cnpj, "SUFRAMA"));
            var company = CnpjaDadosCadastrais.objeto(office.get("company"));
            Fonte simplesData = simples.get();
            if (simplesData.data().containsKey("simples")) company.put("simples", simplesData.data().get("simples"));
            if (simplesData.data().containsKey("simei")) company.put("simei", simplesData.data().get("simei"));
            office.put("company", company);
            Fonte cccData = ccc.get(), sintegraData = sintegra.get(), suframaData = suframa.get();
            office.put("registrations", cccData.data().getOrDefault("registrations", List.of()));
            office.put("sintegra", sintegraData.data().getOrDefault("registrations", List.of()));
            office.put("suframa", suframaData.data());
            for (Fonte fonte : List.of(simplesData, cccData, sintegraData, suframaData)) {
                if (fonte.aviso() != null) avisos.add(fonte.aviso());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            avisos.add("Consulta complementar interrompida. Revise os dados antes de salvar.");
        } catch (ExecutionException exception) {
            avisos.add("Uma consulta complementar não foi concluída. Revise os dados antes de salvar.");
        }
        office.put("avisosConsulta", avisos);
        return avisos;
    }

    private Fonte consultarFonte(String path, String cnpj, String nome) {
        try {
            var data = client.get().uri(path, cnpj).header(HttpHeaders.AUTHORIZATION, apiKey)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return new Fonte(data == null ? Map.of() : data, null);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return new Fonte(Map.of(), nome + ": nenhum registro encontrado.");
            return new Fonte(Map.of(), nome + ": consulta indisponível. Os demais dados foram mantidos.");
        } catch (RestClientException exception) {
            return new Fonte(Map.of(), nome + ": consulta indisponível. Os demais dados foram mantidos.");
        }
    }

    private CnpjEmpresaResponseDTO converter(Map<String, Object> office, List<String> avisos) {
        var company = CnpjaDadosCadastrais.objeto(office.get("company"));
        var address = CnpjaDadosCadastrais.objeto(office.get("address"));
        var phones = office.get("phones") instanceof List<?> list ? list : List.of();
        var emails = office.get("emails") instanceof List<?> list ? list : List.of();
        String phone = phones.stream().map(CnpjaDadosCadastrais::objeto).filter(p -> !texto(p.get("number")).isBlank())
                .map(p -> texto(p.get("area")) + texto(p.get("number"))).findFirst().orElse("");
        String email = emails.stream().map(CnpjaDadosCadastrais::objeto).map(e -> texto(e.get("address"))).filter(e -> !e.isBlank()).findFirst().orElse("");
        var dados = CnpjaDadosCadastrais.semHistoricos(office);
        return new CnpjEmpresaResponseDTO(texto(office.get("taxId")), texto(company.get("name")), texto(office.get("alias")),
                texto(address.get("street")), texto(address.get("number")), texto(address.get("district")), texto(address.get("city")),
                texto(address.get("state")), texto(address.get("zip")), phone, email, CnpjaDadosCadastrais.tipoEmpresa(dados), dados, avisos);
    }

    public byte[] emitirComprovante(String tipo, String cnpj) {
        if (apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta CNPJá não configurada.");
        String path = switch (tipo) {
            case "RFB" -> "/rfb/certificate";
            case "SIMPLES" -> "/simples/certificate";
            case "CCC" -> "/ccc/certificate";
            case "SUFRAMA" -> "/suframa/certificate";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de comprovante inválido.");
        };
        try {
            byte[] pdf = client.get().uri(path + "?taxId={cnpj}", cnpj).header(HttpHeaders.AUTHORIZATION, apiKey)
                    .retrieve().body(byte[].class);
            if (pdf == null || pdf.length < 5 || pdf.length > 20 * 1024 * 1024
                    || !new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O provedor não retornou um PDF válido.");
            }
            return pdf;
        } catch (RestClientResponseException exception) {
            throw erroConsulta(exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível emitir o comprovante. Tente novamente.");
        }
    }

    private record Fonte(Map<String, Object> data, String aviso) {}

    private ResponseStatusException erroConsulta(int status) {
        return switch (status) {
            case 400 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um CNPJ válido.");
            case 404 -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CNPJ não encontrado. Confira o número ou preencha os dados manualmente.");
            case 429 -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de consultas atingido. Aguarde e tente novamente.");
            case 402 -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Créditos de consulta CNPJá indisponíveis. Preencha os dados manualmente.");
            case 401, 403 -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A consulta CNPJá está indisponível. Preencha os dados manualmente.");
            default -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o CNPJ. Tente novamente ou preencha os dados manualmente.");
        };
    }

    private static String texto(Object value) { return value == null ? "" : value.toString().trim(); }
    private record Consulta(@NotBlank @Pattern(regexp = "[0-9]{14}") @CNPJ String cnpj) {}
}
