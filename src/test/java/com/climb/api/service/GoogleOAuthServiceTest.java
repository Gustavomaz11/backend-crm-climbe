package com.climb.api.service;

import com.climb.api.config.GoogleCalendarConfig;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock
    private GoogleCalendarConfig googleCalendarConfig;

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
        usuario.setEmail("usuario@climbe.com.br");
        usuario.setSituacao("ATIVO");

        lenient().when(googleCalendarConfig.isEmailAllowed(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).endsWith("@climbe.com.br"));

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
                .resolverLoginGoogle("google-sub", "usuario@climbe.com.br", "Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_LOGIN_SUCCESS, response.getStatus());
        assertEquals("access-token", response.getLogin().getAccessToken());
        verify(usuarioService, never()).buscarPorEmail(anyString());
    }

    @Test
    void deveLogarComContaGooglePessoalQuandoVinculoJaExiste() {
        Usuario usuarioPessoal = new Usuario();
        usuarioPessoal.setId(2L);
        usuarioPessoal.setEmail("usuario@gmail.com");
        usuarioPessoal.setSituacao("ATIVO");

        UsuarioOAuth vinculo = new UsuarioOAuth();
        vinculo.setUsuario(usuarioPessoal);

        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-pessoal"))
                .thenReturn(Optional.of(vinculo));
        when(authenticationService.validarUsuarioAtivo(usuarioPessoal, "Usuario nao encontrado"))
                .thenReturn(AuthResult.success(null));
        when(authenticationService.gerarRespostaLogin(usuarioPessoal)).thenReturn(AuthResult.success(loginResponse));

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-pessoal", "usuario@gmail.com", "Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_LOGIN_SUCCESS, response.getStatus());
        assertEquals("access-token", response.getLogin().getAccessToken());
    }

    @Test
    void deveReaproveitarConsentimentoGoogleNosProximosLogins() {
        when(googleCalendarConfig.isEnabled()).thenReturn(true);
        when(googleCalendarConfig.getClientId()).thenReturn("client-id");
        when(googleCalendarConfig.getClientSecret()).thenReturn("client-secret");
        when(googleCalendarConfig.getRedirectUri()).thenReturn("https://api.climbe.com/auth/google/callback");

        String authorizationUrl = googleOAuthService.gerarUrlAutorizacao().authorizationUrl();

        assertFalse(authorizationUrl.contains("prompt=consent"));
        assertTrue(authorizationUrl.contains("access_type=offline"));
        assertTrue(authorizationUrl.contains("include_granted_scopes=true"));
    }

    @Test
    void devePersistirPendingQuandoNaoExisteContaNemPending() {
        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(usuarioService.buscarPorEmail("novo@climbe.com.br")).thenReturn(null);

        OAuth2PendingRegistration salvo = pendingFixture(42L, "novo@climbe.com.br", "Novo Usuario", false);
        when(pendingService.criarPendingGoogle("google-sub", "novo@climbe.com.br", "Novo Usuario", "https://img"))
                .thenReturn(salvo);

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "novo@climbe.com.br", "Novo Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_PENDING_APPROVAL, response.getStatus());
        assertEquals("novo@climbe.com.br", response.getEmail());
        verify(pendingService).criarPendingGoogle("google-sub", "novo@climbe.com.br", "Novo Usuario", "https://img");
    }

    @Test
    void deveRetornarPendingApprovalQuandoPendingExisteNaoAprovada() {
        OAuth2PendingRegistration pending = pendingFixture(42L, "novo@climbe.com.br", "Novo Usuario", false);

        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(pending));

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "novo@climbe.com.br", "Novo Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_PENDING_APPROVAL, response.getStatus());
        verify(jwtUtil, never()).generatePendingRegistrationToken(eq(42L), anyString());
        verify(pendingService, never()).criarPendingGoogle(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void deveRetornarCompletarCadastroComTokenQuandoPendingAprovada() {
        OAuth2PendingRegistration pending = pendingFixture(42L, "novo@climbe.com.br", "Novo Usuario", true);

        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(pending));
        when(jwtUtil.generatePendingRegistrationToken(42L, "novo@climbe.com.br"))
                .thenReturn("pending-jwt");

        GoogleOAuthResolveResponseDTO response = googleOAuthService
                .resolverLoginGoogle("google-sub", "novo@climbe.com.br", "Novo Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_COMPLETAR_CADASTRO, response.getStatus());
        assertEquals("pending-jwt", response.getPendingToken());
        assertEquals("novo@climbe.com.br", response.getEmail());
    }

    @Test
    void devePermitirSolicitacaoDeAcessoComContaGooglePessoal() {
        when(usuarioOAuthRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-pessoal"))
                .thenReturn(Optional.empty());
        when(pendingService.findAtivoPorProvider(OAuthProvider.GOOGLE, "google-pessoal"))
                .thenReturn(Optional.empty());
        when(usuarioService.buscarPorEmail("usuario@gmail.com")).thenReturn(null);

        OAuth2PendingRegistration salvo = pendingFixture(43L, "usuario@gmail.com", "Usuario", false);
        salvo.setProviderUserId("google-pessoal");
        when(pendingService.criarPendingGoogle(
                "google-pessoal", "usuario@gmail.com", "Usuario", "https://img"))
                .thenReturn(salvo);

        GoogleOAuthResolveResponseDTO response = googleOAuthService.resolverLoginGoogle(
                "google-pessoal", "usuario@gmail.com", "Usuario", "https://img");

        assertEquals(GoogleOAuthService.STATUS_PENDING_APPROVAL, response.getStatus());
        assertEquals("usuario@gmail.com", response.getEmail());
        verify(pendingService).criarPendingGoogle(
                "google-pessoal", "usuario@gmail.com", "Usuario", "https://img");
    }

    @Test
    void deveSubstituirVinculoPessoalPorContaCorporativaDoUsuario() {
        UsuarioOAuth vinculoPessoal = new UsuarioOAuth();
        vinculoPessoal.setId(9L);
        vinculoPessoal.setUsuario(usuario);
        vinculoPessoal.setProvider(OAuthProvider.GOOGLE);
        vinculoPessoal.setProviderUserId("google-pessoal");
        vinculoPessoal.setEmailProvider("usuario@gmail.com");

        when(usuarioService.buscarPorId(1L)).thenReturn(usuario);
        when(authenticationService.validarUsuarioAtivo(usuario, "Usuario nao encontrado"))
                .thenReturn(AuthResult.success(null));
        when(usuarioOAuthRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE, "google-corporativo"))
                .thenReturn(Optional.empty());
        when(usuarioOAuthRepository.findByUsuarioIdAndProvider(1L, OAuthProvider.GOOGLE))
                .thenReturn(Optional.of(vinculoPessoal));

        GoogleOAuthResolveResponseDTO response = googleOAuthService.vincularConta(
                1L,
                "google-corporativo",
                "usuario@climbe.com.br",
                "Usuario",
                "https://img-corporativa");

        assertEquals(GoogleOAuthService.STATUS_LINK_SUCCESS, response.getStatus());
        assertEquals("google-corporativo", vinculoPessoal.getProviderUserId());
        assertEquals("usuario@climbe.com.br", vinculoPessoal.getEmailProvider());
        verify(usuarioOAuthRepository).save(vinculoPessoal);
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
