package com.climb.api.service;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CnpjaServiceTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final String CNPJ = "37335118000180";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CnpjaService service = new CnpjaService("chave-isolada-de-teste", VALIDATORS.getValidator(), builder);

    @AfterAll static void fechar() { VALIDATORS.close(); }

    @Test void usaApiComercialComChaveNoCabecalhoERetornaSomenteCamposDoCadastro() {
        server.expect(requestTo("https://api.cnpja.com/office/" + CNPJ))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "chave-isolada-de-teste"))
                .andRespond(withSuccess("""
                        {"taxId":"37335118000180","alias":"Empresa Exemplo","company":{"name":"Empresa Exemplo Ltda.","members":[]},
                         "address":{"street":"Rua Central","number":"123","district":"Centro","city":"Aracaju","state":"SE","zip":"49010000","details":"Sala 2"},
                         "phones":[{"area":"79","number":"999991234","type":"MOBILE"}],"emails":[{"address":"contato@example.test"}],"status":{"id":2}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(4), anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var result = service.consultar(CNPJ);
        assertThat(result.razaoSocial()).isEqualTo("Empresa Exemplo Ltda.");
        assertThat(result.nomeFantasia()).isEqualTo("Empresa Exemplo");
        assertThat(result.logradouro()).isEqualTo("Rua Central");
        assertThat(result.numero()).isEqualTo("123");
        assertThat(result.cidade()).isEqualTo("Aracaju");
        assertThat(result.uf()).isEqualTo("SE");
        assertThat(result.cep()).isEqualTo("49010000");
        assertThat(result.telefone()).isEqualTo("79999991234");
        assertThat(result.email()).isEqualTo("contato@example.test");
        server.verify();
    }

    @Test void aceitaContatosEEnderecoAusentes() {
        server.expect(anything()).andRespond(withSuccess("""
                {"taxId":"37335118000180","company":{"name":"Empresa Ltda."}}
                """, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(4), anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var result = service.consultar(CNPJ);
        assertThat(result.nomeFantasia()).isEmpty();
        assertThat(result.logradouro()).isEmpty();
        assertThat(result.email()).isEmpty();
        assertThat(result.telefone()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"37335118000181", "00000000000000", "123", "37.335.118/0001-80"})
    void naoConsomeConsultaComCnpjInvalido(String cnpj) {
        assertThatThrownBy(() -> service.consultar(cnpj)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        server.verify();
    }

    @Test void naoConsultaSemChaveConfigurada() {
        var unconfigured = new CnpjaService("", VALIDATORS.getValidator(), builder);
        assertThatThrownBy(() -> unconfigured.consultar(CNPJ)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @ParameterizedTest @ValueSource(ints = {401, 402, 403, 404, 429, 500})
    void traduzFalhasSemRepassarRespostaOuCredenciaisDoProvedor(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("detalhe privado do provedor"));
        assertThatThrownBy(() -> service.consultar(CNPJ)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getReason()).doesNotContain("chave-isolada", "detalhe privado");
            assertThat(e.getStatusCode().value()).isEqualTo(status == 404 || status == 429 ? status : status == 500 ? 502 : 503);
        });
        server.verify();
    }

    @Test void rejeitaRespostaDeOutroCnpj() {
        server.expect(anything()).andRespond(withSuccess("""
                {"taxId":"11222333000181","company":{"name":"Outra Empresa"}}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.consultar(CNPJ)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
