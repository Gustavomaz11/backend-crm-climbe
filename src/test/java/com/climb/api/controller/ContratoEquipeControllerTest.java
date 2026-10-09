package com.climb.api.controller;

import com.climb.api.model.dto.ContratoEquipeRequestDTO;
import com.climb.api.service.ContratoEquipeService;
import com.climb.api.service.ContratoRateioTecnicoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.mockito.Mockito.*;

class ContratoEquipeControllerTest {
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test
    void usaIdDosDetailsDoJwtEmTodosOsEndpointsEmVezDoEmailPrincipal() {
        var equipe = mock(ContratoEquipeService.class);
        var rateio = mock(ContratoRateioTecnicoService.class);
        var controller = new ContratoEquipeController(equipe, rateio);
        var auth = new UsernamePasswordAuthenticationToken("lider@example.test", null, List.of());
        auth.setDetails(42L); SecurityContextHolder.getContext().setAuthentication(auth);
        controller.listar(); controller.buscar(12L);
        controller.salvar(12L, new ContratoEquipeRequestDTO(List.of(7L, 8L)));
        controller.consultarRateio(12L, "2026-10");
        verify(equipe).listarDisponiveis(42L);
        verify(equipe).buscarEquipe(12L, 42L);
        verify(equipe).salvarEquipe(12L, 42L, List.of(7L, 8L));
        verify(rateio).consultar(12L, "2026-10", 42L);
    }
}
