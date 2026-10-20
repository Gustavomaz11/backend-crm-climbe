package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.model.*;
import com.climb.api.repository.UsuarioOAuthRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleDriveCredentialServiceTest {
    private final GoogleDriveProperties properties = new GoogleDriveProperties();
    private final UsuarioOAuthRepository vinculos = mock(UsuarioOAuthRepository.class);
    private final GoogleCredentialService tokens = mock(GoogleCredentialService.class);
    private final GoogleDriveCredentialService service = new GoogleDriveCredentialService(properties, vinculos, tokens);

    @Test void usaExatamenteOEmailConfiguradoComAutorizacaoDrive() {
        properties.setAccountEmail("automacao@example.test");
        var usuario = new Usuario(); usuario.setId(7L);
        var vinculo = new UsuarioOAuth(); vinculo.setUsuario(usuario);
        vinculo.setScopes("openid email https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/calendar");
        when(vinculos.findByProviderAndEmailProviderIgnoreCase(OAuthProvider.GOOGLE, "automacao@example.test")).thenReturn(Optional.of(vinculo));
        when(tokens.obterAccessToken(7L)).thenReturn(Optional.of("token"));
        assertThat(service.obterAccessToken()).isEqualTo("token");
        verify(tokens).obterAccessToken(7L);
    }

    @Test void pedeNovaAutorizacaoSeContaTemApenasPermissaoDeAgenda() {
        properties.setAccountEmail("automacao@example.test");
        var vinculo = new UsuarioOAuth(); vinculo.setScopes("openid https://www.googleapis.com/auth/calendar");
        when(vinculos.findByProviderAndEmailProviderIgnoreCase(OAuthProvider.GOOGLE, "automacao@example.test")).thenReturn(Optional.of(vinculo));
        assertThatThrownBy(service::obterAccessToken).hasMessageContaining("Entre novamente");
        verifyNoInteractions(tokens);
    }

    @Test void naoEscolheContaDeOutroUsuarioQuandoAContaResponsavelNaoEstaVinculada() {
        properties.setAccountEmail("automacao@example.test");
        assertThatThrownBy(service::obterAccessToken).hasMessageContaining("conta responsável");
        verifyNoInteractions(tokens);
    }
}
