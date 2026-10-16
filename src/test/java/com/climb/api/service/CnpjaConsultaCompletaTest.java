package com.climb.api.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CnpjaConsultaCompletaTest {
    @Test void agregaTodosOsServicosAtuaisDescartaHistoricosENaoBuscaPdfs() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(requestTo("https://api.cnpja.com/office/37335118000180")).andRespond(withSuccess("""
                {"taxId":"37335118000180","company":{"name":"Empresa","members":[{"person":{"name":"Ana"}}]},"status":{"id":8,"text":"Baixada"},"mainActivity":{"id":6201501,"text":"Desenvolvimento"}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cnpja.com/simples?taxId=37335118000180&history=false")).andRespond(withSuccess("""
                {"simples":{"optant":true,"since":"2024-01-01","history":[{"since":"2000-01-01"}]},"simei":{"optant":true,"since":"2024-01-01"}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cnpja.com/ccc?taxId=37335118000180&states=ALL&source=CCC")).andRespond(withSuccess("""
                {"registrations":[{"number":"12345678","state":"SE","enabled":true}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cnpja.com/ccc?taxId=37335118000180&states=ALL&source=SINTEGRA")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.cnpja.com/suframa?taxId=37335118000180")).andRespond(withSuccess("""
                {"number":"12345","status":{"text":"Ativa"},"incentives":[{"tax":"ICMS","benefit":"Isenção"}]}
                """, MediaType.APPLICATION_JSON));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var result = new CnpjaService("teste", factory.getValidator(), builder).consultar("37335118000180");
            assertThat(result.tipoEmpresa()).isEqualTo("MEI");
            assertThat(result.dadosCadastrais().toString()).contains("Baixada", "12345678", "Isenção", "Ana").doesNotContain("history", "2000-01-01");
            assertThat(result.avisos()).hasSize(1).first().asString().contains("SINTEGRA");
        }
        server.verify();
    }

    @Test void solicitaPdfAutenticadoApenasNoEndpointDeComprovante() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.cnpja.com/rfb/certificate?taxId=37335118000180")).andExpect(header("Authorization", "teste"))
                .andRespond(withSuccess("%PDF-1.4\n%%EOF".getBytes(), MediaType.APPLICATION_PDF));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(new CnpjaService("teste", factory.getValidator(), builder).emitirComprovante("RFB", "37335118000180")).startsWith("%PDF-".getBytes());
        }
        server.verify();
    }
}
