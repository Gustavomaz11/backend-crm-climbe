package com.climb.api.controller;
import com.climb.api.service.*;
import com.climb.api.model.dto.GoogleAuthorizationUrlResponseDTO;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@ExtendWith(MockitoExtension.class)
class GoogleAuthControllerTest {
    @Mock GoogleOAuthService google;
    @Mock OAuthLoginRequestService requests;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.standaloneSetup(new GoogleAuthController(google,requests)).build(); }

    @Test void inicioUsaStateEPkceECookieProtegido() throws Exception {
        when(requests.iniciar("app-challenge")).thenReturn(new OAuthLoginRequestService.Inicio("state", "browser", "google-challenge"));
        when(google.gerarUrlAutorizacao("state","google-challenge")).thenReturn(new GoogleAuthorizationUrlResponseDTO("https://accounts.google.com/auth", "callback", "openid"));
        mvc.perform(get("/auth/google/start").secure(true).param("codeChallenge","app-challenge"))
                .andExpect(status().isFound()).andExpect(header().string("Location","https://accounts.google.com/auth"))
                .andExpect(header().string("Set-Cookie",containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",containsString("Secure")))
                .andExpect(header().string("Set-Cookie",containsString("SameSite=Lax")));
    }

    @Test void callbackSemCookieNaoTrocaCodigoGoogle() throws Exception {
        when(requests.consumir("state",null)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        when(google.gerarRedirecionamentoErro(anyString())).thenReturn(URI.create("https://app.test/login?error=invalid"));
        mvc.perform(get("/auth/google/callback").param("state","state").param("code","malicious-code"))
                .andExpect(status().isFound()).andExpect(header().string("Location",containsString("error=invalid")));
        verify(google,never()).resolverCallbackGoogle(anyString(),anyString(),anyString());
    }

    @Test void callbackValidoUsaVerifierArmazenadoEChallengeDoNavegador() throws Exception {
        when(requests.consumir("state","browser")).thenReturn(new OAuthLoginRequestService.Validado("provider-verifier","app-challenge"));
        when(google.resolverCallbackGoogle("code","provider-verifier","app-challenge")).thenReturn(URI.create("https://app.test/login?code=exchange"));
        mvc.perform(get("/auth/google/callback").param("state","state").param("code","code")
                .cookie(new Cookie(GoogleAuthController.BINDING_COOKIE,"browser")))
                .andExpect(status().isFound()).andExpect(header().string("Location",containsString("code=exchange")))
                .andExpect(header().string("Set-Cookie",containsString("Max-Age=0")));
    }
}
