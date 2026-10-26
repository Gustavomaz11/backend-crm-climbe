package com.climb.api.controller;

import com.climb.api.model.dto.ConviteLoteRequestDTO;
import com.climb.api.security.PermissaoAcesso;
import com.climb.api.service.ConviteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConviteControllerTest {
    @Configuration @EnableMethodSecurity static class Seguranca {}

    @Test void aceitaLoteValidadoENormalizadoParaEnvioEmSegundoPlano() throws Exception {
        var service = mock(ConviteService.class);
        var request = new ConviteLoteRequestDTO(List.of("socio@example.com"));
        when(service.convidar(request)).thenReturn(new ConviteService.Resultado(1, 0));
        MockMvcBuilders.standaloneSetup(new ConviteController(service)).build()
                .perform(post("/convites").contentType("application/json").content("{\"emails\":[\" Socio@example.com \"]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.agendados").value(1));
        verify(service).convidar(request);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"emails\":[]}", "{\"emails\":[null]}",
            "{\"emails\":[\"\"]}", "{\"emails\":[\"invalido\"]}",
            "{\"emails\":[\"valido@example.com\",\"invalido\"]}"})
    void rejeitaLoteInteiroSemEnfileirarQuandoAlgumEmailEInvalido(String json) throws Exception {
        var service = mock(ConviteService.class);
        MockMvcBuilders.standaloneSetup(new ConviteController(service)).build()
                .perform(post("/convites").contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void rejeitaMaisDeCemDestinatarios() throws Exception {
        var service = mock(ConviteService.class);
        String emails = IntStream.range(0, 101).mapToObj(i -> "\"socio" + i + "@example.com\"").collect(Collectors.joining(","));
        MockMvcBuilders.standaloneSetup(new ConviteController(service)).build()
                .perform(post("/convites").contentType("application/json").content("{\"emails\":[" + emails + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void exigePermissaoAdministrativaMesmoNaChamadaDiretaDaApi() {
        var service = mock(ConviteService.class);
        var permissao = mock(PermissaoAcesso.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Seguranca.class);
            context.registerBean("permissaoAcesso", PermissaoAcesso.class, () -> permissao);
            context.registerBean(ConviteController.class, () -> new ConviteController(service));
            context.refresh();
            var controller = context.getBean(ConviteController.class);
            var request = new ConviteLoteRequestDTO(List.of("socio@example.com"));
            assertThatThrownBy(() -> controller.convidar(request)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(service);
            when(permissao.tem("PERMITIR_ACESSO")).thenReturn(true);
            controller.convidar(request);
            verify(service).convidar(request);
        }
    }
}
