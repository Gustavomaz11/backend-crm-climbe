package com.climb.api.service;

import com.climb.api.config.GoogleCalendarConfig;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.UsuarioOAuth;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoogleCredentialServiceTest {

    @Mock private UsuarioOAuthRepository usuarioOAuthRepository;
    @Mock private OAuth2PendingRegistrationRepository pendingRepository;
    @Mock private GoogleCalendarConfig config;

    @Test
    void deveObterTokenValidoSomenteDoUsuarioAutenticado() {
        TokenEncryptionService encryption = new TokenEncryptionService(
                "segredo-de-testes-com-pelo-menos-trinta-e-dois-bytes");
        UsuarioOAuth vinculo = new UsuarioOAuth();
        vinculo.setEmailProvider("usuario@climbe.com.br");
        vinculo.setAccessTokenCriptografado(encryption.criptografar("token-do-usuario-7"));
        vinculo.setAccessTokenExpiraEm(LocalDateTime.now().plusMinutes(30));
        when(usuarioOAuthRepository.findByUsuarioIdAndProvider(7L, OAuthProvider.GOOGLE))
                .thenReturn(Optional.of(vinculo));
        when(config.isEmailAllowed("usuario@climbe.com.br")).thenReturn(true);

        GoogleCredentialService service = new GoogleCredentialService(
                usuarioOAuthRepository,
                pendingRepository,
                encryption,
                config);

        assertEquals("token-do-usuario-7", service.obterAccessToken(7L).orElseThrow());
    }

    @Test
    void naoDeveUsarTokenDeContaGooglePessoal() {
        TokenEncryptionService encryption = new TokenEncryptionService(
                "segredo-de-testes-com-pelo-menos-trinta-e-dois-bytes");
        UsuarioOAuth vinculo = new UsuarioOAuth();
        vinculo.setEmailProvider("usuario@gmail.com");
        vinculo.setAccessTokenCriptografado(encryption.criptografar("token-pessoal"));
        vinculo.setAccessTokenExpiraEm(LocalDateTime.now().plusMinutes(30));
        when(usuarioOAuthRepository.findByUsuarioIdAndProvider(7L, OAuthProvider.GOOGLE))
                .thenReturn(Optional.of(vinculo));
        when(config.isEmailAllowed("usuario@gmail.com")).thenReturn(false);

        GoogleCredentialService service = new GoogleCredentialService(
                usuarioOAuthRepository,
                pendingRepository,
                encryption,
                config);

        assertTrue(service.obterAccessToken(7L).isEmpty());
    }

    @Test
    void naoDevePersistirCredencialDeAgendaParaContaGooglePessoal() {
        TokenEncryptionService encryption = new TokenEncryptionService(
                "segredo-de-testes-com-pelo-menos-trinta-e-dois-bytes");
        UsuarioOAuth vinculo = new UsuarioOAuth();
        vinculo.setEmailProvider("usuario@gmail.com");
        when(usuarioOAuthRepository.findByUsuarioIdAndProvider(7L, OAuthProvider.GOOGLE))
                .thenReturn(Optional.of(vinculo));
        when(config.isEmailAllowed("usuario@gmail.com")).thenReturn(false);

        GoogleCredentialService service = new GoogleCredentialService(
                usuarioOAuthRepository,
                pendingRepository,
                encryption,
                config);

        service.salvarParaUsuario(7L, "token-pessoal", "refresh-pessoal", 3600L, "calendar");

        assertNull(vinculo.getAccessTokenCriptografado());
        verify(usuarioOAuthRepository, never()).save(vinculo);
    }

    @Test
    void naoDevePersistirCredencialDeAgendaNoCadastroPendentePessoal() {
        TokenEncryptionService encryption = new TokenEncryptionService(
                "segredo-de-testes-com-pelo-menos-trinta-e-dois-bytes");
        OAuth2PendingRegistration pending = new OAuth2PendingRegistration();
        pending.setEmail("usuario@gmail.com");
        when(config.isEmailAllowed("usuario@gmail.com")).thenReturn(false);

        GoogleCredentialService service = new GoogleCredentialService(
                usuarioOAuthRepository,
                pendingRepository,
                encryption,
                config);

        service.salvarParaPending(pending, "token-pessoal", "refresh-pessoal", 3600L, "calendar");

        assertNull(pending.getAccessTokenCriptografado());
        verify(pendingRepository, never()).save(pending);
    }
}
