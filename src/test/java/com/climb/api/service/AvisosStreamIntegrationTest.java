package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.NotificacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false", "logging.level.root=WARN",
        "spring.datasource.url=jdbc:h2:mem:avisos_stream;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.avisos.intervalo-ms=86400000", "app.pipeline.cadencia.atraso-inicial-ms=86400000",
        "app.notificacoes.email.enabled=false", "app.reunioes.convites.cron=-", "app.reunioes.aceites.cron=-"
})
@ActiveProfiles("test")
class AvisosStreamIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filtros;
    @Autowired AvisosStreamService stream;
    @Autowired SessaoAutenticacaoService sessoes;
    @Autowired NotificacaoService notificacoes;
    @Autowired NotificacaoRepository repository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    private MockMvc mvc;
    private TransactionTemplate transacao;
    private record Sessao(Usuario usuario, com.climb.api.model.dto.RefreshTokenResponseDTO tokens) {}

    @BeforeEach void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filtros).build();
        transacao = new TransactionTemplate(manager);
    }
    @AfterEach void fechar() { stream.encerrar(); }

    @Test void publicaSomenteAposCommitParaOUsuarioCorretoESemDuplicarLote() throws Exception {
        var pessoa = sessao(); var outra = sessao();
        var destino = abrir(pessoa); var terceiro = abrir(outra);
        transacao.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) salvarAviso(pessoa.usuario());
            em.flush(); stream.processar();
            assertThat(new String(destino.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("event:notificacoes");
        });
        stream.processar();
        assertThat(destino.getResponse().getContentAsString()).containsOnlyOnce("event:notificacoes");
        assertThat(terceiro.getResponse().getContentAsString()).doesNotContain("event:notificacoes");
        assertThat(destino.getResponse().getHeader("Cache-Control")).contains("no-transform");
        assertThat(destino.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
    }

    @Test void rollbackNaoGeraEventoEHistoricoOfflineApareceNoProximoAcesso() throws Exception {
        var pessoa = sessao(); var destino = abrir(pessoa);
        transacao.executeWithoutResult(status -> { salvarAviso(pessoa.usuario()); em.flush(); status.setRollbackOnly(); });
        stream.processar();
        assertThat(destino.getResponse().getContentAsString()).doesNotContain("event:notificacoes");
        assertThat(notificacoes.listarPorUsuario(pessoa.usuario().getId())).isEmpty();
        stream.encerrar();
        Long id = transacao.execute(status -> salvarAviso(pessoa.usuario()).getIdNotificacao());
        var retorno = abrir(pessoa);
        assertThat(retorno.getResponse().getContentAsString()).contains("event:sincronizar");
        mvc.perform(get("/notificacoes/minhas").header("Authorization", bearer(pessoa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].lida").value(false));
        notificacoes.marcarComoLidaDoUsuario(id, pessoa.usuario().getId());
        stream.processar();
        assertThat(retorno.getResponse().getContentAsString()).contains("event:notificacoes");
        assertThat(notificacoes.listarPorUsuario(pessoa.usuario().getId()).getFirst().getLida()).isTrue();
    }

    @Test void conviteNovoEAceiteAtualizamOCanalDoConvidado() throws Exception {
        var pessoa = sessao(); var destino = abrir(pessoa);
        Long id = transacao.execute(status -> {
            var reuniao = new Reuniao(); reuniao.setTitulo("Convite SSE");
            var data = LocalDateTime.now().plusDays(1); reuniao.setData(data.toLocalDate()); reuniao.setHora(data.toLocalTime());
            reuniao.setDataHoraFim(data.plusHours(1)); em.persist(reuniao);
            var convite = new ParticipanteReuniao(); convite.setReuniao(reuniao); convite.setUsuario(pessoa.usuario());
            em.persist(convite); return convite.getId();
        });
        stream.processar();
        assertThat(destino.getResponse().getContentAsString()).containsOnlyOnce("event:convites");
        transacao.executeWithoutResult(status -> {
            em.find(ParticipanteReuniao.class, id).setResposta(com.climb.api.model.enums.RespostaConviteReuniao.ACEITA);
        });
        stream.processar();
        assertThat(destino.getResponse().getContentAsString().split("event:convites", -1)).hasSize(3);
    }

    @Test void leituraEmLoteTambemAtualizaOutrasAbas() throws Exception {
        var pessoa = sessao(); transacao.executeWithoutResult(status -> salvarAviso(pessoa.usuario()));
        var destino = abrir(pessoa);
        notificacoes.marcarTodasComoLidasDoUsuario(pessoa.usuario().getId());
        stream.processar();
        assertThat(destino.getResponse().getContentAsString()).contains("event:notificacoes");
        assertThat(repository.countByUsuario_IdAndLidaFalse(pessoa.usuario().getId())).isZero();
    }

    @Test void aprovacaoDeContratoAtualizaSinoEAcessoKanbanSomenteDoDestinatario() throws Exception {
        var pessoa = sessao(); var terceiro = sessao(); var destino = abrir(pessoa); var outro = abrir(terceiro);
        transacao.executeWithoutResult(status -> {
            var aviso = new Notificacao(); aviso.setUsuario(pessoa.usuario()); aviso.setTipo("CONTRATO_APROVADO");
            aviso.setMensagem("Contrato aprovado"); aviso.setDestino("/contratos/kanban?contrato=10&servico=CFO");
            repository.saveAndFlush(aviso); stream.processar();
            assertThat(new String(destino.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("event:kanban");
        });
        stream.processar();
        assertThat(destino.getResponse().getContentAsString()).contains("event:notificacoes", "event:kanban");
        assertThat(outro.getResponse().getContentAsString()).doesNotContain("event:notificacoes", "event:kanban");
    }

    @Test void enviaEventosPelaMesmaRespostaHttpSemEsperarOEncerramentoDoStream() throws Exception {
        var pessoa = sessao();
        int porta = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + porta + "/avisos/stream"))
                .header("Authorization", bearer(pessoa)).header("Accept", "text/event-stream").GET().build();
        try (var client = java.net.http.HttpClient.newHttpClient();
             var body = client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream())
                     .get(10, java.util.concurrent.TimeUnit.SECONDS).body()) {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(body, java.nio.charset.StandardCharsets.UTF_8));
            var recebimento = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    String linha;
                    while ((linha = reader.readLine()) != null) if (linha.equals("event:notificacoes")) return linha;
                    return "encerrado";
                } catch (java.io.IOException erro) { throw new java.util.concurrent.CompletionException(erro); }
            });
            transacao.executeWithoutResult(status -> salvarAviso(pessoa.usuario()));
            stream.processar();
            assertThat(recebimento.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("event:notificacoes");
        }
    }

    @Test void sessaoRevogadaNaoRecebeEventosERequisicaoAnonimaOuRefreshNaoAbreCanal() throws Exception {
        mvc.perform(get("/avisos/stream")).andExpect(status().isUnauthorized());
        var pessoa = sessao(); var destino = abrir(pessoa);
        mvc.perform(get("/avisos/stream").header("Authorization", "Bearer " + pessoa.tokens().refreshToken()))
                .andExpect(status().isUnauthorized());
        sessoes.encerrar(pessoa.tokens().refreshToken());
        transacao.executeWithoutResult(status -> salvarAviso(pessoa.usuario()));
        stream.processar();
        assertThat(destino.getResponse().getContentAsString()).doesNotContain("event:notificacoes");
        mvc.perform(get("/avisos/stream").header("Authorization", bearer(pessoa))).andExpect(status().isUnauthorized());
    }

    private MvcResult abrir(Sessao pessoa) throws Exception {
        return mvc.perform(get("/avisos/stream").header("Authorization", bearer(pessoa)))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
    }
    private String bearer(Sessao pessoa) { return "Bearer " + pessoa.tokens().accessToken(); }
    private Notificacao salvarAviso(Usuario usuario) {
        var aviso = new Notificacao(); aviso.setUsuario(usuario); aviso.setMensagem("Aviso pendente"); aviso.setTipo("GERAL");
        return repository.save(aviso);
    }
    private Sessao sessao() {
        return transacao.execute(status -> {
            var usuario = new Usuario(); usuario.setNomeCompleto("Pessoa SSE"); usuario.setEmail(UUID.randomUUID() + "@avisos.test");
            usuario.setContato("71999999999"); usuario.setSituacao("ATIVO"); usuario.setSenhaHash("teste"); em.persist(usuario);
            return new Sessao(usuario, sessoes.criar(usuario));
        });
    }
}
