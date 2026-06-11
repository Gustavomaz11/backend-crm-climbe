package com.climb.api.controller;

import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.LoginRequestDTO;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.RefreshTokenRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.service.AuthenticationService;
import com.climb.api.service.GoogleOAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void deveRealizarLoginNormal() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("usuario@teste.com");
        request.setSenha("senha123");

        LoginResponseDTO response = criarLoginResponse();
        when(authenticationService.autenticar("usuario@teste.com", "senha123"))
                .thenReturn(AuthResult.success(response));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.usuario.email").value("usuario@teste.com"));
    }

    @Test
    void deveRenovarToken() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setRefreshToken("refresh-token");

        when(authenticationService.refreshAccessToken(eq("refresh-token")))
                .thenReturn(AuthResult.success("novo-access-token"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("novo-access-token"));
    }

    private LoginResponseDTO criarLoginResponse() {
        UsuarioResponseDTO usuario = new UsuarioResponseDTO();
        usuario.setId(1L);
        usuario.setNomeCompleto("Usuario Teste");
        usuario.setEmail("usuario@teste.com");
        usuario.setCpf("12345678900");
        usuario.setContato("85999999999");
        usuario.setSituacao("ATIVO");

        return new LoginResponseDTO("access-token", "refresh-token", usuario, 900000);
    }
}
