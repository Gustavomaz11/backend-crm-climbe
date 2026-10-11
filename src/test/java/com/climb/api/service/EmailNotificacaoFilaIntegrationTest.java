package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false", "logging.level.root=WARN",
        "app.notificacoes.email.enabled=false", "app.pipeline.cadencia.atraso-inicial-ms=86400000"
})
@ActiveProfiles("test")
class EmailNotificacaoFilaIntegrationTest {
    @Autowired NotificacaoEntregaService entrega;
    @Autowired EmailNotificacaoFilaService fila;
    @Autowired EmailNotificacaoPendenteRepository pendentes;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean EmailService email;
    private Usuario usuario;

    @BeforeEach void preparar() {
        reset(email);
        pendentes.deleteAll(); notificacoes.deleteAll();
        usuario = new Usuario(); usuario.setNomeCompleto("Pessoa"); usuario.setEmail("pessoa-fila@example.test");
        usuario.setContato("11999999999"); usuario.setSenhaHash("test"); usuario.setSituacao("ATIVO");
        usuario = usuarios.saveAndFlush(usuario);
    }
    @AfterEach void limpar() { pendentes.deleteAll(); notificacoes.deleteAll(); usuarios.delete(usuario); }

    @Test void salvarRetornaSemEnviarEmailEWorkerNaoMantemTransacaoDuranteSMTP() throws Exception {
        var iniciou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            when(email.enviarEmailComBotao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> {
                        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        iniciou.countDown();
                        assertThat(liberar.await(10, TimeUnit.SECONDS)).isTrue();
                        return true;
                    });
            new TransactionTemplate(manager).executeWithoutResult(status -> notificar());
            assertThat(pendentes.count()).isEqualTo(1);
            assertThat(notificacoes.count()).isEqualTo(1);
            verifyNoInteractions(email);
            var envio = executor.submit(() -> processador().processarPendentes());
            assertThat(iniciou.await(10, TimeUnit.SECONDS)).isTrue();
            // Mesmo durante um envio lento, outra instância não reserva o mesmo e-mail.
            assertThat(fila.reservar()).isEmpty();
            liberar.countDown(); envio.get(10, TimeUnit.SECONDS);
            assertThat(pendentes.count()).isZero();
            verify(email).enviarEmailComBotao(eq(usuario.getEmail()), eq("Atribuição"), eq("Atribuição"), eq("Você foi atribuído"),
                    eq("Abrir no Climbe"), eq("https://app.example.test/contratos/kanban?contrato=1&tarefaId=2"), anyString());
        } finally { liberar.countDown(); executor.shutdownNow(); }
    }

    @Test void rollbackCancelaNotificacaoEEmailPendente() {
        new TransactionTemplate(manager).executeWithoutResult(status -> { notificar(); status.setRollbackOnly(); });
        assertThat(pendentes.count()).isZero(); assertThat(notificacoes.count()).isZero();
        processador().processarPendentes(); verifyNoInteractions(email);
    }

    @Test void falhaDeSMTPPreservaEmailEAgendaNovaTentativa() {
        notificar();
        when(email.enviarEmailComBotao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        processador().processarPendentes();
        var pendente = pendentes.findAll().getFirst();
        assertThat(pendente.getTentativas()).isEqualTo(1);
        assertThat(pendente.getProximaTentativa()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(fila.reservar()).isEmpty();
        pendente.setProximaTentativa(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)); pendentes.saveAndFlush(pendente);
        when(email.enviarEmailComBotao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        processador().processarPendentes(); assertThat(pendentes.count()).isZero();
    }

    @Test void reservaInterrompidaPodeSerRetomadaSemConfirmacaoAntigaApagarNovoEnvio() {
        notificar();
        var primeira = fila.reservar().orElseThrow();
        var pendente = pendentes.findById(primeira.id()).orElseThrow();
        pendente.setProximaTentativa(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)); pendentes.saveAndFlush(pendente);
        var segunda = fila.reservar().orElseThrow();
        fila.confirmar(primeira); assertThat(pendentes.count()).isEqualTo(1);
        fila.confirmar(segunda); assertThat(pendentes.count()).isZero();
    }

    private EmailNotificacaoProcessador processador() {
        return new EmailNotificacaoProcessador(fila, email, "https://app.example.test/", true);
    }
    private void notificar() {
        entrega.notificar(usuario, "TAREFA_ATRIBUIDA", "Atribuição", "Você foi atribuído", "/contratos/kanban?contrato=1&tarefaId=2");
    }
}
