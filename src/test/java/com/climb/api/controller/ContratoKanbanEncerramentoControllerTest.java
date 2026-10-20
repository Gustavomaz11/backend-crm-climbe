package com.climb.api.controller;

import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.ContratoServicoEncerramentoService;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContratoKanbanEncerramentoControllerTest {
    private final ContratoServicoEncerramentoService service = mock(ContratoServicoEncerramentoService.class);
    private MockMvc mvc;
    @BeforeEach void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new ContratoKanbanEncerramentoController(service, new AuthenticatedUserProvider())).build();
        var auth = new UsernamePasswordAuthenticationToken("teste", null); auth.setDetails(7L);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void usaUsuarioAutenticadoEServicoDoCorpo() throws Exception {
        mvc.perform(patch("/contratos/10/kanban/encerrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"servico\":\"CFO\",\"usuarioId\":99}"))
                .andExpect(status().isOk());
        verify(service).encerrar(10L, ServicoComercial.CFO, 7L);
    }

    @Test void recusaDeResponsabilidadeRetorna403() throws Exception {
        when(service.encerrar(10L, ServicoComercial.CFO, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o responsável pode encerrar."));
        mvc.perform(patch("/contratos/10/kanban/encerrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"servico\":\"CFO\"}"))
                .andExpect(status().isForbidden());
    }
}
