package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleParticipanteDisponibilidadeServiceTest {
    @Mock GoogleCalendarService calendar;
    @Mock GoogleCredentialService credentials;
    @InjectMocks GoogleParticipanteDisponibilidadeService service;
    static final Instant INICIO = Instant.parse("2026-10-20T13:00:00Z");
    static final Instant FIM = Instant.parse("2026-10-20T15:00:00Z");
    static final String EMAIL = "ana@example.test";

    @ParameterizedTest
    @CsvSource({"accepted,false", "needsAction,true", "tentative,true", "declined,true"})
    void ocupaSomenteSeOProprioParticipanteAceitou(String status, boolean disponivel) throws Exception {
        var request = prepararAgenda("token", EMAIL, List.of(evento(status)));
        assertThat(consultar()).containsEntry(2L, disponivel);
        verify(request).setTimeMin(new DateTime(INICIO.toEpochMilli()));
        verify(request).setTimeMax(new DateTime(FIM.toEpochMilli()));
        verify(request).setSingleEvents(true);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void respostaAusenteNaoViraAceiteNemDisponibilidadeConfirmada(String status) throws Exception {
        prepararAgenda("token", EMAIL, List.of(evento(status)));
        assertThat(consultar()).containsEntry(2L, null);
    }

    @Test void aceiteDeOutroConvidadoNaoOcupaUsuarioPendente() throws Exception {
        var event = evento("needsAction");
        event.setAttendees(List.of(new EventAttendee().setEmail("bruno@example.test").setResponseStatus("accepted"),
                new EventAttendee().setEmail(EMAIL).setResponseStatus("needsAction")));
        prepararAgenda("token", EMAIL, List.of(event));
        assertThat(consultar()).containsEntry(2L, true);
    }

    @Test void consultaAgendaPropriaDoUsuarioConectadoEIdentificaRespostaPorSelf() throws Exception {
        when(credentials.obterAccessToken(2L)).thenReturn(Optional.of("token-ana"));
        var event = new Event().setId("evento").setAttendees(List.of(new EventAttendee()
                .setEmail("alias@example.test").setSelf(true).setResponseStatus("accepted")));
        prepararAgenda("token-ana", "primary", List.of(event));
        assertThat(consultar()).containsEntry(2L, false);
        verify(calendar, never()).buildCalendar("token");
    }

    @Test void eventosCanceladosOuMarcadosComoLivreNaoOcupamMesmoAceitos() throws Exception {
        prepararAgenda("token", EMAIL, List.of(evento("accepted").setStatus("cancelled"),
                evento("accepted").setTransparency("transparent")));
        assertThat(consultar()).containsEntry(2L, true);
    }

    @Test void seguePaginacaoEAvaliaAceiteNoFinalDoIntervalo() throws Exception {
        var request = prepararAgenda("token", EMAIL, List.of());
        when(request.execute()).thenReturn(new Events().setItems(List.of(evento("needsAction"))).setNextPageToken("segunda"),
                new Events().setItems(List.of(evento("accepted"))));
        assertThat(consultar()).containsEntry(2L, false);
        verify(request).setPageToken("segunda");
    }

    @Test void edicaoDesconsideraProprioEventoSemEsconderOutroAceito() throws Exception {
        var original = new Event().setId("original").setICalUID("uid");
        when(calendar.buscarEvento("original", "token")).thenReturn(original);
        var copia = evento("accepted").setId("copia").setICalUID("uid");
        var request = prepararAgenda("token", EMAIL, List.of(copia));
        assertThat(service.consultar("token", List.of(usuario()), INICIO, FIM, "original")).containsEntry(2L, true);
        when(request.execute()).thenReturn(new Events().setItems(List.of(copia, evento("accepted").setId("outro"))));
        assertThat(service.consultar("token", List.of(usuario()), INICIO, FIM, "original")).containsEntry(2L, false);
    }

    @Test void permissaoApenasDeOcupacaoNaoPermiteInferirAceite() throws Exception {
        var request = prepararAgenda("token", EMAIL, List.of());
        when(request.execute()).thenReturn(new Events().setAccessRole("freeBusyReader").setItems(List.of(new Event())));
        assertThat(consultar()).containsEntry(2L, null);
    }

    @Test void convidadosOmitidosOuEventoPrivadoNaoPermitemInferirAceite() throws Exception {
        prepararAgenda("token", EMAIL, List.of(new Event().setAttendeesOmitted(true), new Event().setVisibility("private")));
        assertThat(consultar()).containsEntry(2L, null);
    }

    @Test void falhaDeConsultaNaoAfirmaQueUsuarioEstaLivre() throws Exception {
        when(calendar.buildCalendar("token")).thenThrow(new IOException("offline"));
        assertThat(consultar()).containsEntry(2L, null);
    }

    @Test void semParticipantesNaoConsultaCredenciaisOuGoogle() {
        assertThat(service.consultar("token", List.of(), INICIO, FIM, null)).isEmpty();
        verifyNoInteractions(calendar, credentials);
    }

    private java.util.Map<Long, Boolean> consultar() {
        return service.consultar("token", List.of(usuario()), INICIO, FIM, null);
    }

    private Calendar.Events.List prepararAgenda(String token, String agendaId, List<Event> eventos) throws Exception {
        var client = mock(Calendar.class);
        var events = mock(Calendar.Events.class);
        var request = mock(Calendar.Events.List.class, Answers.RETURNS_SELF);
        when(calendar.buildCalendar(token)).thenReturn(client);
        when(client.events()).thenReturn(events);
        when(events.list(agendaId)).thenReturn(request);
        when(request.execute()).thenReturn(new Events().setItems(eventos));
        return request;
    }

    private Event evento(String status) {
        return new Event().setId("evento").setAttendees(List.of(new EventAttendee().setEmail(EMAIL).setResponseStatus(status)));
    }

    private Usuario usuario() {
        var usuario = new Usuario(); usuario.setId(2L); usuario.setEmail(EMAIL); return usuario;
    }
}
