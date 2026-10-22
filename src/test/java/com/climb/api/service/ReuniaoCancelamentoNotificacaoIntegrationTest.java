package com.climb.api.service;

import com.climb.api.model.Notificacao;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Reuniao;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.EmailNotificacaoPendenteRepository;
import com.climb.api.repository.NotificacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false", "logging.level.root=WARN",
        "spring.datasource.url=jdbc:h2:mem:reuniao_cancelamento;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.notificacoes.email.enabled=false", "app.pipeline.cadencia.atraso-inicial-ms=86400000",
        "app.reunioes.convites.cron=-", "app.reunioes.aceites.cron=-", "app.reunioes.respostas-google.cron=-"
})
@ActiveProfiles("test")
@Transactional
class ReuniaoCancelamentoNotificacaoIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ReuniaoService service;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired EmailNotificacaoPendenteRepository pendentes;
    @MockitoBean EmailService email;

    @Test void cancelamentoPersisteAvisoEEmailSomenteParaQuemAceitouSemDuplicarAoRepetir() {
        var reuniao = cenario();

        service.cancelar(reuniao.getIdReuniao(), reuniao.getOrganizadorId());
        service.cancelar(reuniao.getIdReuniao(), reuniao.getOrganizadorId());
        em.flush(); em.clear();

        var avisos = notificacoes.findAll();
        assertThat(avisos).extracting(aviso -> aviso.getUsuario().getEmail())
                .containsExactlyInAnyOrder("ana@cancelamento.test", "bia@cancelamento.test");
        assertThat(avisos).allSatisfy(aviso -> {
            assertThat(aviso.getTipo()).isEqualTo("REUNIAO_CANCELADA");
            assertThat(aviso.getLida()).isFalse();
            assertThat(aviso.getDestino()).isEqualTo("/agenda?reuniaoId=" + reuniao.getIdReuniao());
            assertThat(aviso.getMensagem()).contains("Planejamento", "15/11/2026", "14:30", "foi cancelada",
                    "Você pode liberar esse horário na sua agenda.");
        });
        assertThat(avisos).extracting(Notificacao::getMensagem).anySatisfy(m -> assertThat(m).startsWith("Olá, Ana!"));
        assertThat(pendentes.findAll()).extracting(item -> item.getDestinatario())
                .containsExactlyInAnyOrder("ana@cancelamento.test", "bia@cancelamento.test");
        assertThat(em.find(Reuniao.class, reuniao.getIdReuniao()).getStatus()).isEqualTo("CANCELADA");
        verifyNoInteractions(email);
    }

    @Test void rollbackDoCancelamentoTambemDescartaNotificacoesEEmails() {
        var reuniao = cenario();
        service.cancelar(reuniao.getIdReuniao(), reuniao.getOrganizadorId());
        em.flush();
        assertThat(notificacoes.count()).isEqualTo(2);
        assertThat(pendentes.count()).isEqualTo(2);

        TestTransaction.flagForRollback(); TestTransaction.end();

        assertThat(notificacoes.count()).isZero();
        assertThat(pendentes.count()).isZero();
        verifyNoInteractions(email);
    }

    private Reuniao cenario() {
        var organizador = usuario("Organizador");
        var reuniao = new Reuniao(); reuniao.setTitulo("Planejamento"); reuniao.setOrganizadorId(organizador.getId());
        reuniao.setData(LocalDate.of(2026, 11, 15)); reuniao.setHora(LocalTime.of(14, 30)); reuniao.setStatus("AGENDADA");
        em.persist(reuniao);
        participante(reuniao, organizador, RespostaConviteReuniao.ACEITA);
        participante(reuniao, usuario("Ana"), RespostaConviteReuniao.ACEITA);
        participante(reuniao, usuario("Bia"), RespostaConviteReuniao.ACEITA);
        participante(reuniao, usuario("Caio"), RespostaConviteReuniao.PENDENTE);
        participante(reuniao, usuario("Davi"), RespostaConviteReuniao.RECUSADA);
        participante(reuniao, usuario("Eva"), RespostaConviteReuniao.TENTATIVA);
        em.flush();
        return reuniao;
    }

    private Usuario usuario(String nome) {
        var usuario = new Usuario(); usuario.setNomeCompleto(nome); usuario.setEmail(nome.toLowerCase(java.util.Locale.ROOT) + "@cancelamento.test");
        usuario.setContato("11999999999"); usuario.setSenhaHash("test"); usuario.setSituacao("ATIVO");
        em.persist(usuario); return usuario;
    }

    private void participante(Reuniao reuniao, Usuario usuario, RespostaConviteReuniao resposta) {
        var participante = new ParticipanteReuniao(); participante.setReuniao(reuniao); participante.setUsuario(usuario);
        participante.setResposta(resposta); em.persist(participante);
    }
}
