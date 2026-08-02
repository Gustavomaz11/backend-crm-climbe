package com.climb.api.controller;

import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.service.AuthenticationService;
import com.climb.api.service.OAuth2PendingService;
import com.climb.api.service.RbacService;
import com.climb.api.service.SolicitacaoAcessoService;
import com.climb.api.service.UsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock private UsuarioService usuarioService;
    @Mock private RbacService rbacService;
    @Mock private OAuth2PendingService pendingService;
    @Mock private AuthenticationService authenticationService;
    @Mock private SolicitacaoAcessoService solicitacaoAcessoService;

    @InjectMocks
    private UsuarioController usuarioController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(usuarioController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveAceitarSolicitacaoComEmailDeQualquerDominio() throws Exception {
        UsuarioRequestDTO request = request("pessoa@gmail.com");
        when(usuarioService.criarSolicitacaoAcesso(any(UsuarioRequestDTO.class)))
                .thenReturn("Solicitacao enviada");

        mockMvc.perform(post("/usuarios")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().json("\"Solicitacao enviada\""));
    }

    @Test
    void deveRetornarConflitoComMensagemQuandoCpfOuEmailJaExiste() throws Exception {
        UsuarioRequestDTO request = request("pessoa@gmail.com");
        when(usuarioService.criarSolicitacaoAcesso(any(UsuarioRequestDTO.class)))
                .thenThrow(new IllegalStateException("CPF ja cadastrado"));

        mockMvc.perform(post("/usuarios")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("CPF ja cadastrado"));
    }

    @Test
    void deveRetornarErroDeValidacaoComMensagem() throws Exception {
        UsuarioRequestDTO request = request("email-invalido");
        when(usuarioService.criarSolicitacaoAcesso(any(UsuarioRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Email invalido"));

        mockMvc.perform(post("/usuarios")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email invalido"));
    }

    @Test
    void deveRecusarSolicitacaoManualComRespostaPadronizada() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn(3L);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(rbacService.temPermissao(3L, com.climb.api.model.PermissaoCodigo.PERMITIR_ACESSO))
                .thenReturn(true);

        mockMvc.perform(post("/usuarios/6/recusar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Solicitacao de acesso recusada"));

        verify(usuarioService).recusarUsuario(6L, 3L);
    }

    private UsuarioRequestDTO request(String email) {
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNomeCompleto("Pessoa Externa");
        request.setCpf("98765432100");
        request.setEmail(email);
        request.setContato("11999999999");
        request.setSenha("senha-segura");
        request.setCargoId(24L);
        return request;
    }
}
