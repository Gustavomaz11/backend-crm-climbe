package com.climb.api.controller;

import com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.service.ContratoKanbanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContratoKanbanServicoControllerTest {
    private final ContratoKanbanService service = mock(ContratoKanbanService.class);
    private MockMvc mvc;
    @BeforeEach void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new ContratoKanbanController(service)).build();
        var auth = new UsernamePasswordAuthenticationToken("teste", null); auth.setDetails(7L);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @org.junit.jupiter.api.AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void aberturaEArquivadosUsamServicoSolicitado() throws Exception {
        mvc.perform(get("/contratos/10/kanban/abertura").param("servico", "CFO")).andExpect(status().isOk());
        mvc.perform(get("/contratos/10/kanban").param("servico", "CONTABILIDADE")).andExpect(status().isOk());
        mvc.perform(get("/contratos/10/kanban/arquivados").param("servico", "CFO")).andExpect(status().isOk());
        verify(service).buscarAbertura(10L, 7L, ServicoComercial.CFO);
        verify(service).buscarBoard(10L, 7L, ServicoComercial.CONTABILIDADE);
        verify(service).listarArquivados(10L, 7L, ServicoComercial.CFO);
    }

    @Test void colunaNovaRecebeServicoNoCorpoDaRequisicao() throws Exception {
        mvc.perform(post("/contratos/10/kanban/raias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"A fazer\",\"servico\":\"CFO\"}"))
                .andExpect(status().isOk());
        verify(service).criarRaia(10L, 7L, new ContratoKanbanRaiaRequestDTO("A fazer", null, null, ServicoComercial.CFO));
    }
}
