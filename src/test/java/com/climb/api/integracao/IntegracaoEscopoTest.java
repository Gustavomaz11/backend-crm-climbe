package com.climb.api.integracao;

import com.climb.api.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class IntegracaoEscopoTest {
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void getPermiteSomenteEmpresasSelecionadas() {
        var escopo = escopo("GET", Set.of("GET", "POST"), Set.of(1L));
        assertThat(escopo.permite(1L)).isTrue(); assertThat(escopo.permite(2L)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void gravacaoDispensaListaDeEmpresasMesmoComGetNoToken(String metodo) {
        assertThat(escopo(metodo, Set.of("GET", metodo), Set.of(1L)).permite(2L)).isTrue();
        assertThat(escopo(metodo, Set.of(metodo), Set.of()).permite(2L)).isTrue();
    }
    @Test void metodoNaoConcedidoOuDesconhecidoNaoLiberaEmpresas() {
        assertThat(escopo("GET", Set.of("POST"), Set.of(1L)).permite(1L)).isFalse();
        assertThat(escopo("TRACE", Set.of("TRACE"), Set.of()).permite(1L)).isFalse();
        assertThat(escopo("POST", Set.of("POST"), Set.of()).permite(null)).isFalse();
    }
    private IntegracaoEscopo escopo(String metodo, Set<String> metodos, Set<Long> empresas) {
        var cliente = new IntegracaoCliente(); cliente.setMetodos(metodos); cliente.setEmpresaIds(empresas);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(cliente, null));
        return new IntegracaoEscopo(mock(ContratoRepository.class), mock(PropostaRepository.class), mock(EmpresaRepository.class),
                mock(PipelineVendasNegocioRepository.class), new MockHttpServletRequest(metodo, "/api/integracoes/v1/exemplo"));
    }
}
