package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.*;
import com.climb.api.security.TokenSeguro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false", "logging.level.root=WARN",
        "spring.datasource.url=jdbc:h2:mem:auth_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.pipeline.cadencia.atraso-inicial-ms=86400000", "app.pipeline.task-reminders.weekly-cron=-",
        "app.pipeline.task-reminders.overdue-cron=-", "app.kanban.prazos.cron=-", "app.kanban.arquivamento.cron=-"})
@ActiveProfiles("test") @Transactional
class AuthSessionSecurityIntegrationTest {
    @Autowired SessaoAutenticacaoService sessions;
    @Autowired SessaoAutenticacaoRepository sessionRepository;
    @Autowired UsuarioRepository users;
    @Autowired JwtUtil jwt;
    @Autowired OAuthLoginRequestService oauth;
    @Autowired OAuthLoginRequestRepository oauthRepository;
    @Autowired OAuth2ExchangeCodeRepository exchangeRepository;
    @Autowired GoogleOAuthService google;
    @MockitoBean RbacService rbac;
    @MockitoBean EmailService email;
    @MockitoBean CloudflareR2ArquivoStorageService storage;
    @MockitoBean ZapSignClient signatures;

    @Test void rotacionaRefreshERevogaTodaSessaoAoDetectarReutilizacao() {
        var user = user(); var original = sessions.criar(user);
        var sid = jwt.extractSessionId(original.accessToken());
        assertThat(sessions.estaAtiva(sid, user.getId())).isTrue();
        var renewed = sessions.renovar(original.refreshToken()).orElseThrow();
        assertThat(renewed.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(sessionRepository.findById(sid).orElseThrow().getRefreshHash()).isEqualTo(TokenSeguro.hash(renewed.refreshToken()));
        assertThat(sessions.renovar(original.refreshToken())).isEmpty();
        assertThat(sessions.estaAtiva(sid, user.getId())).isFalse();
        assertThat(sessions.renovar(renewed.refreshToken())).isEmpty();
    }

    @Test void logoutInvalidaAccessERefreshSemAfetarOutraSessao() {
        var user=user(); var first=sessions.criar(user); var second=sessions.criar(user);
        sessions.encerrar(first.refreshToken());
        assertThat(sessions.estaAtiva(jwt.extractSessionId(first.accessToken()), user.getId())).isFalse();
        assertThat(sessions.renovar(first.refreshToken())).isEmpty();
        assertThat(sessions.estaAtiva(jwt.extractSessionId(second.accessToken()), user.getId())).isTrue();
    }

    @Test void trocarSenhaRevogarEReativarNaoRestauraTokensAntigos() {
        var user=user(); var first=sessions.criar(user);
        user.setSenhaHash("outro-hash"); users.saveAndFlush(user);
        assertThat(sessions.estaAtiva(jwt.extractSessionId(first.accessToken()), user.getId())).isFalse();
        assertThat(sessions.renovar(first.refreshToken())).isEmpty();
        var second=sessions.criar(user);
        user.setSituacao("REVOGADO"); user.setSituacao("ATIVO"); users.saveAndFlush(user);
        assertThat(sessions.estaAtiva(jwt.extractSessionId(second.accessToken()), user.getId())).isFalse();
        assertThat(sessions.renovar(second.refreshToken())).isEmpty();
    }

    @Test void alteracaoDeEmailExpiracaoOuDonoErradoInvalidamSessao() {
        var user=user(); var first=sessions.criar(user); var sid=jwt.extractSessionId(first.accessToken());
        assertThat(sessions.estaAtiva(sid, user.getId()+1)).isFalse();
        user.setEmail("novo-"+UUID.randomUUID()+"@test.local"); users.saveAndFlush(user);
        assertThat(sessions.estaAtiva(sid,user.getId())).isFalse();
        var second=sessions.criar(user); var row=sessionRepository.findById(jwt.extractSessionId(second.accessToken())).orElseThrow();
        row.setExpiraEm(Instant.now().minusSeconds(1)); sessionRepository.saveAndFlush(row);
        assertThat(sessions.estaAtiva(row.getId(),user.getId())).isFalse();
        assertThat(sessions.renovar(second.refreshToken())).isEmpty();
    }

    @Test void oauthExigeStateDoMesmoNavegadorEPermiteUmUnicoConsumo() {
        String appChallenge=TokenSeguro.challenge(TokenSeguro.aleatorio());
        var flow=oauth.iniciar(appChallenge);
        assertThatThrownBy(() -> oauth.consumir(flow.state(),TokenSeguro.aleatorio())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> oauth.consumir(flow.state(),null)).isInstanceOf(ResponseStatusException.class);
        var verified=oauth.consumir(flow.state(),flow.browserBinding());
        assertThat(verified.appChallenge()).isEqualTo(appChallenge);
        assertThat(TokenSeguro.challenge(verified.codeVerifier())).isEqualTo(flow.googleChallenge());
        assertThat(oauthRepository.findById(TokenSeguro.hash(flow.state()))).isEmpty();
        assertThatThrownBy(() -> oauth.consumir(flow.state(),flow.browserBinding())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void oauthRejeitaStateExpiradoEChallengeInvalido() {
        assertThatThrownBy(() -> oauth.iniciar("invalido")).isInstanceOf(ResponseStatusException.class);
        var flow=oauth.iniciar(TokenSeguro.challenge(TokenSeguro.aleatorio()));
        var row=oauthRepository.findById(TokenSeguro.hash(flow.state())).orElseThrow();
        row.setExpiraEm(Instant.now().minusSeconds(1)); oauthRepository.saveAndFlush(row);
        assertThatThrownBy(() -> oauth.consumir(flow.state(),flow.browserBinding())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void trocaDeCodigoExigeVerifierDoFrontendEPermiteUmUnicoUso() {
        String verifier=TokenSeguro.aleatorio();var row=new OAuth2ExchangeCode();
        row.setCode(TokenSeguro.aleatorio());row.setAppChallenge(TokenSeguro.challenge(verifier));
        row.setAccessToken("token-isolado");row.setCriadoEm(LocalDateTime.now());row.setExpiraEm(LocalDateTime.now().plusSeconds(60));
        exchangeRepository.saveAndFlush(row);
        assertThatThrownBy(()->google.exchangeCode(row.getCode(),TokenSeguro.aleatorio())).isInstanceOf(RuntimeException.class);
        assertThat(row.getConsumido()).isFalse();
        assertThat(google.exchangeCode(row.getCode(),verifier).accessToken()).isEqualTo("token-isolado");
        assertThat(row.getConsumido()).isTrue();
        assertThatThrownBy(()->google.exchangeCode(row.getCode(),verifier)).isInstanceOf(RuntimeException.class);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void refreshConcorrenteNaoPermiteReutilizacaoNemDuasRotacoes() throws Exception {
        var user=user();var tokens=sessions.criar(user);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->{ start.await(); return sessions.renovar(tokens.refreshToken()); });
            var second=executor.submit(()->{ start.await(); return sessions.renovar(tokens.refreshToken()); });
            start.countDown();
            var a=first.get(10,TimeUnit.SECONDS);var b=second.get(10,TimeUnit.SECONDS);
            assertThat(a.isPresent() ^ b.isPresent()).isTrue();
            assertThat(sessions.estaAtiva(jwt.extractSessionId(tokens.accessToken()),user.getId())).isFalse();
        }
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void atualizacaoConcorrenteNaoRestauraVersaoAntigaDeAutenticacao() {
        var user=user();var tokens=sessions.criar(user);
        var stale=users.findById(user.getId()).orElseThrow();
        user.setSenhaHash("senha-nova");users.saveAndFlush(user);
        stale.setNomeCompleto("Atualização simultânea do perfil");
        assertThatThrownBy(()->users.saveAndFlush(stale))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(sessions.estaAtiva(jwt.extractSessionId(tokens.accessToken()),user.getId())).isFalse();
        assertThat(sessions.renovar(tokens.refreshToken())).isEmpty();
    }

    private Usuario user() {
        var u=new Usuario();u.setNomeCompleto("Teste isolado"); u.setContato("85999999999");
        u.setEmail(UUID.randomUUID()+"@test.local");u.setSituacao("ATIVO");u.setSenhaHash("hash-teste");
        return users.saveAndFlush(u);
    }
}
