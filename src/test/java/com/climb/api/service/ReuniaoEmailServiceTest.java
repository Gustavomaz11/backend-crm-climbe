package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Usuario;
import com.climb.api.model.enums.RespostaConviteReuniao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReuniaoEmailServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notificaSomenteConvidadosMesmoQuandoOrganizadorEstaEntreParticipantes(boolean presencial) {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(presencial); reuniao.setLocal("Sala 2");
        reuniao.setOrganizadorId(1L); reuniao.setLinkMeet("https://meet.google.com/abc-defg-hij");
        Usuario organizador = new Usuario(); organizador.setId(1L); organizador.setEmail("organizador@example.test");
        Usuario convidado = new Usuario(); convidado.setId(2L); convidado.setEmail("convidado@example.test");

        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(organizador, convidado));

        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), anyString(), eq("/agenda?reuniaoId=3"));
        verify(entrega).avisarPorEmail(eq(GoogleCalendarService.EMAIL_OBRIGATORIO), anyString(), anyString(), anyString());
        verifyNoMoreInteractions(entrega);
        org.junit.jupiter.api.Assertions.assertTrue(reuniao.isConvitesNotificados());
    }
    @Test void organizadorDaContaContatoNaoRecebeConviteDuplicadoPorEmail() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(true); reuniao.setLocal("Sala 2"); reuniao.setOrganizadorId(1L);
        Usuario organizador = new Usuario(); organizador.setId(1L); organizador.setEmail(GoogleCalendarService.EMAIL_OBRIGATORIO);
        Usuario convidado = new Usuario(); convidado.setId(2L); convidado.setEmail("convidado@example.test");

        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(organizador, convidado));

        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), anyString(), anyString());
        verifyNoMoreInteractions(entrega);
    }
    @Test void aguardaMeetParaNaoEnviarConviteSemLink() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(false);
        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of());
        verifyNoInteractions(entrega);
        org.junit.jupiter.api.Assertions.assertFalse(reuniao.isConvitesNotificados());
    }
    @Test void avisaConvidadosComDataHoraELocalSemDependerDeMeet() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(true); reuniao.setLocal("Sala 2");
        Usuario convidado = new Usuario(); convidado.setId(1L); convidado.setEmail("pessoa@example.test");
        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(convidado));
        verify(entrega).notificar(eq(convidado), eq("REUNIAO_AGENDADA"), anyString(), argThat(m -> m.contains("15/11/2026") && m.contains("14:30") && m.contains("Sala 2")), eq("/agenda?reuniaoId=3"));
        verify(entrega).avisarPorEmail(eq("contato@climbe.com.br"), anyString(), anyString(), anyString());
    }
    @Test void enviaLinkMeetENaoDuplicaConviteAoContatoCadastrado() {
        var entrega = mock(NotificacaoEntregaService.class);
        Reuniao reuniao = reuniao(); reuniao.setPresencial(false); reuniao.setLinkMeet("https://meet.google.com/abc-defg-hij");
        Usuario contato = new Usuario(); contato.setId(2L); contato.setEmail("contato@climbe.com.br");
        new ReuniaoEmailService(entrega).notificarAgendamento(reuniao, List.of(contato));
        verify(entrega).notificar(eq(contato), anyString(), anyString(), contains("https://meet.google.com/abc-defg-hij"), anyString());
        verifyNoMoreInteractions(entrega);
    }

    @Test void cancelamentoAvisaCadaConvidadoConfirmadoComMensagemPessoalSemDuplicarOuAvisarOrganizador() {
        var entrega = mock(NotificacaoEntregaService.class);
        var reuniao = reuniao(); reuniao.setOrganizadorId(1L); reuniao.setStatus("CANCELADA");
        var ana = participante(2L, "  Ana   Silva  ", RespostaConviteReuniao.ACEITA);
        var bruno = participante(3L, "Bruno Souza", RespostaConviteReuniao.ACEITA);
        var pendente = participante(4L, "Caio", RespostaConviteReuniao.PENDENTE);
        var recusou = participante(5L, "Davi", RespostaConviteReuniao.RECUSADA);
        var talvez = participante(6L, "Eva", RespostaConviteReuniao.TENTATIVA);
        var organizador = participante(1L, "Organizador", RespostaConviteReuniao.ACEITA);
        var duplicado = participante(2L, "Ana Silva", RespostaConviteReuniao.ACEITA);

        new ReuniaoEmailService(entrega).notificarCancelamento(reuniao,
                List.of(organizador, ana, pendente, recusou, talvez, bruno, duplicado));

        verify(entrega).notificar(ana.getUsuario(), "REUNIAO_CANCELADA", "Reunião cancelada: Planejamento",
                "Olá, Ana! A reunião \"Planejamento\", marcada para 15/11/2026 às 14:30, foi cancelada. Você pode liberar esse horário na sua agenda.",
                "/agenda?reuniaoId=3");
        verify(entrega).notificar(eq(bruno.getUsuario()), eq("REUNIAO_CANCELADA"), eq("Reunião cancelada: Planejamento"),
                startsWith("Olá, Bruno!"), eq("/agenda?reuniaoId=3"));
        verifyNoMoreInteractions(entrega);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void saudacaoDeCancelamentoPermaneceNaturalQuandoConvidadoNaoTemNome(String nome) {
        var entrega = mock(NotificacaoEntregaService.class);
        var convidado = participante(2L, nome, RespostaConviteReuniao.ACEITA);
        new ReuniaoEmailService(entrega).notificarCancelamento(reuniao(), List.of(convidado));
        verify(entrega).notificar(eq(convidado.getUsuario()), eq("REUNIAO_CANCELADA"), anyString(),
                startsWith("Olá! A reunião \"Planejamento\""), eq("/agenda?reuniaoId=3"));
        verifyNoMoreInteractions(entrega);
    }

    @Test void naoCriaAvisosDeCancelamentoQuandoNinguemConfirmouPresenca() {
        var entrega = mock(NotificacaoEntregaService.class);
        new ReuniaoEmailService(entrega).notificarCancelamento(reuniao(),
                List.of(participante(2L, "Ana", RespostaConviteReuniao.PENDENTE),
                        participante(3L, "Bruno", RespostaConviteReuniao.RECUSADA)));
        verifyNoInteractions(entrega);
    }

    private ParticipanteReuniao participante(Long id, String nome, RespostaConviteReuniao resposta) {
        var usuario = new Usuario(); usuario.setId(id); usuario.setNomeCompleto(nome); usuario.setEmail(id + "@example.test");
        var participante = new ParticipanteReuniao(); participante.setUsuario(usuario); participante.setResposta(resposta);
        return participante;
    }
    private Reuniao reuniao() { Reuniao r = new Reuniao(); r.setIdReuniao(3L); r.setTitulo("Planejamento"); r.setData(LocalDate.of(2026,11,15)); r.setHora(LocalTime.of(14,30)); return r; }
}
