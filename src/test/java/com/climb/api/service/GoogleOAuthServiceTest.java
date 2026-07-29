package com.climb.api.service;

import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.Usuario;
import com.climb.api.model.UsuarioOAuth;
import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.GoogleOAuthResolveResponseDTO;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock
    private UsuarioOAuthRepository usuarioOAuthRepository;

    @Mock
    private OAuth2PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private OAuth2PendingService pendingService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GoogleCredentialService googleCredentialService;

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    private Usuario usuario;
    private LoginResponseDTO loginResponse;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("usuario@teste.com");
        usuario.setSituacao("ATIVO");

        loginResponse = new LoginResponseDTO();
        loginResponse.setAccessToken("access-token");
        loginResponse.setRefreshToken("refresh-token");
        loginResponse.setExpiresIn(900000);
    }

    @Test
    void deveLogarQuandoJaExisteVinculoGoogle() {
        UsuarioOAuth vinculo = new UsuarioOAuth();
        vinculo.setUsuario(usuario);

        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(vinculo));
        when(authenticationService.validarUsuarioAtivo(usuario, "Usuario nao encontrado"))
                .thenReturn(AuthResult.success(null));
        when(authenticationService.gerarRespostaLogin(usuario)).thenReturn(AuthResult.success(loginResponse));

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "usuario@teste.com", "Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_LOGIN_SUCCESS, response.getStatus());
        assertEquals("access-token", response.getLogin().getAccessToken());
        verify(usuarioService, never()).buscarPorEmail(anyString());
    }

    @Test
    void devePersistirPendingQuandoNaoExisteContaNemPending() {
        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(usuarioService.buscarPorEmail("novo@teste.com")).thenReturn(null);

        OAuth2PendingRegistration salvo = pendingFixture(42L, "novo@teste.com", "Novo Usuario", false);
        when(pendingService.criarPendingGoogle("google-sub", "novo@teste.com", "Novo Usuario", "https://img"))
                .thenReturn(salvo);

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "novo@teste.com", "Novo Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_PENDING_APPROVAL, response.getStatus());
        assertEquals("novo@teste.com", response.getEmail());
        verify(pendingService).criarPendingGoogle("google-sub", "novo@teste.com", "Novo Usuario", "https://img");
    }

    @Test
    void deveRetornarPendingApprovalQuandoPendingExisteNaoAprovada() {
        OAuth2PendingRegistration pending = pendingFixture(42L, "novo@teste.com", "Novo Usuario", false);

        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(pending));

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "novo@teste.com", "Novo Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_PENDING_APPROVAL, response.getStatus());
        verify(jwtUtil, never()).generatePendingRegistrationToken(eq(42L), anyString());
        verify(pendingService, never()).criarPendingGoogle(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void deveRetornarCompletarCadastroComTokenQuandoPendingAprovada() {
        OAuth2PendingRegistration pending = pendingFixture(42L, "novo@teste.com", "Novo Usuario", true);

        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(pending));
        when(jwtUtil.generatePendingRegistrationToken(42L, "novo@teste.com"))
                .thenReturn("pending-jwt");

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "novo@teste.com", "Novo Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_COMPLETAR_CADASTRO, response.getStatus());
        assertEquals("pending-jwt", response.getPendingToken());
        assertEquals("novo@teste.com", response.getEmail());
    }

    private OAuth2PendingRegistration pendingFixture(Long id, String email, String nome, boolean aprovado) {
        OAuth2PendingRegistration pending = new OAuth2PendingRegistration();
        pending.setId(id);
        pending.setProvider(OAuthProvider.GOOGLE);
        pending.setProviderUserId("google-sub");
        pending.setEmail(email);
        pending.setNome(nome);
        pending.setAvatarUrl("https://img");
        pending.setExpiraEm(LocalDateTime.now().plusDays(7));
        pending.setConsumido(false);
        pending.setAprovado(aprovado);
        pending.setCriadoEm(LocalDateTime.now());
        return pending;
    }
}
