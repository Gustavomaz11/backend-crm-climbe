package com.climb.api.controller;

import com.climb.api.model.dto.PipelineResponsavelServicoRequestDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineVendasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineGanhoServicoControllerTest {
    private final PipelineVendasService service = mock(PipelineVendasService.class);
    private MockMvc mvc;
    private final List<PipelineResponsavelServicoRequestDTO> responsaveis = List.of(
            new PipelineResponsavelServicoRequestDTO(ServicoComercial.CONTABILIDADE, 2L),
            new PipelineResponsavelServicoRequestDTO(ServicoComercial.CFO, 3L));
    private static final String CORPO = """
            {"propostaId":40,"etapaId":20,"responsaveisPorServico":[
                {"servico":"CONTABILIDADE","responsavelTecnicoId":2},
                {"servico":"CFO","responsavelTecnicoId":3}]}
            """;

    @BeforeEach void configurar() {
        var autenticado = mock(AuthenticatedUserProvider.class);
        when(autenticado.getUserId()).thenReturn(7L);
        mvc = MockMvcBuilders.standaloneSetup(new PipelineVendasController(service, autenticado)).build();
    }

    @Test void ganhoRecebeResponsavelDeCadaServico() throws Exception {
        mvc.perform(patch("/pipeline-vendas/negocios/10/ganhar").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());
        verify(service).marcarResultado(10L, 7L, PipelineVendasResultado.GANHO, null, null, null, 40L, responsaveis);
    }

    @Test void movimentacaoRecebeResponsavelDeCadaServico() throws Exception {
        mvc.perform(patch("/pipeline-vendas/negocios/10/etapa").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());
        verify(service).mover(10L, 7L, 20L, null, null, null, 40L, responsaveis);
    }
}
