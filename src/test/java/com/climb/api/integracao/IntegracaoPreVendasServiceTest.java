package com.climb.api.integracao;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.*;
import com.climb.api.service.PipelineVendasService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegracaoPreVendasServiceTest {
    PipelineVendasFunilRepository funis = mock(PipelineVendasFunilRepository.class);
    PipelineVendasEtapaRepository etapas = mock(PipelineVendasEtapaRepository.class);
    PipelineVendasService negocios = mock(PipelineVendasService.class);
    IntegracaoEscopo escopo = mock(IntegracaoEscopo.class);
    IntegracaoPreVendasService service = new IntegracaoPreVendasService(funis, etapas, negocios, escopo);
    PipelineVendasFunil funil;
    PipelineVendasEtapa lista;
    IntegracaoLeadRequest dados = new IntegracaoLeadRequest(7L, "Contato", "79999991234", "contato@example.com", 99L, "Integração", "Contato direto", null);

    @BeforeEach void setup() {
        funil = new PipelineVendasFunil(); funil.setIdFunil(80L); funil.setTipo("PRE_VENDAS");
        lista = new PipelineVendasEtapa(); lista.setIdEtapa(120L); lista.setResultado(PipelineVendasResultado.ABERTO);
        when(funis.findByCodigoAndAtivoTrue("PRE_VENDAS")).thenReturn(Optional.of(funil));
        when(etapas.findByFunilIdFunilAndCodigoAndAtivoTrue(80L, "LISTA_LEADS")).thenReturn(Optional.of(lista));
        var empresa = new Empresa(); empresa.setIdEmpresa(7L); empresa.setNomeFantasia("Empresa exemplo");
        when(escopo.empresa(7L)).thenReturn(empresa); when(escopo.ator()).thenReturn(99L);
    }
    @Test void resolveDestinoPelosCodigosEDelegaCriacaoComDadosDoLead() {
        service.criar(dados);
        var payload = ArgumentCaptor.forClass(PipelineNegocioRequestDTO.class);
        verify(negocios).criar(eq(99L), payload.capture());
        assertThat(payload.getValue().funilId()).isEqualTo(80L);
        assertThat(payload.getValue().etapaId()).isEqualTo(120L);
        assertThat(payload.getValue().empresaId()).isEqualTo(7L);
        assertThat(payload.getValue().nomeContato()).isEqualTo("Contato");
        assertThat(payload.getValue().nomeEmpresa()).isEqualTo("Empresa exemplo");
    }
    @Test void funilAusenteNaoCriaNegocioEmOutroDestino() {
        when(funis.findByCodigoAndAtivoTrue("PRE_VENDAS")).thenReturn(Optional.empty());
        conflito(); verifyNoInteractions(etapas, negocios, escopo);
    }
    @Test void funilComTipoIncorretoNaoRecebeLead() {
        funil.setTipo("VENDAS"); conflito(); verifyNoInteractions(negocios);
    }
    @Test void etapaAusenteNaoUsaOutraEtapaAberta() {
        when(etapas.findByFunilIdFunilAndCodigoAndAtivoTrue(80L, "LISTA_LEADS")).thenReturn(Optional.empty());
        conflito(); verifyNoInteractions(negocios);
    }
    @Test void etapaConcluidaNaoRecebeLead() {
        lista.setResultado(PipelineVendasResultado.GANHO); conflito(); verifyNoInteractions(negocios);
    }
    private void conflito() {
        assertThatThrownBy(() -> service.criar(dados)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
}
