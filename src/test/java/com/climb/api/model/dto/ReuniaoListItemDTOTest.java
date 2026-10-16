package com.climb.api.model.dto;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReuniaoListItemDTOTest {
    @Test void localPreenchidoClassificaEventoComMeetComoPresencialSemRecursoDeSala() {
        String local = "Escritório Climbe Cont - Hermes Fontes-2-Sala de Reunião - Climbe Cont (6)";
        var evento = new Event().setId("evento-com-local").setLocation(local)
                .setHangoutLink("https://meet.google.com/abc-defg-hij");
        var resumo = ReuniaoListItemDTO.fromGoogleEventExterno(evento);
        assertThat(resumo.getPresencial()).isTrue();
        assertThat(resumo.getLocal()).isEqualTo(local);
        assertThat(resumo.getLinkMeet()).isEqualTo(evento.getHangoutLink());
    }

    @Test void corrigeClassificacaoDeReuniaoSalvaComLocalEFlagVirtual() {
        var reuniao = new com.climb.api.model.Reuniao();
        reuniao.setPresencial(false); reuniao.setLocal("Sala 1");
        reuniao.setLinkMeet("https://meet.google.com/abc-defg-hij");
        var resumo = ReuniaoListItemDTO.fromEntity(reuniao);
        assertThat(resumo.getPresencial()).isTrue();
        assertThat(resumo.getLinkMeet()).isEqualTo(reuniao.getLinkMeet());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"  "})
    void localVazioNaoTransformaEventoVirtualEmPresencial(String local) {
        var evento = new Event().setId("virtual").setLocation(local).setHangoutLink("https://meet.google.com/abc-defg-hij");
        assertThat(ReuniaoListItemDTO.fromGoogleEventExterno(evento).getPresencial()).isFalse();
    }
    @Test void usaSalaFisicaComoLocalMesmoQuandoTambemExisteGoogleMeet() {
        String nome = "Escritório Climbe Cont - Hermes Fontes-2-Sala de Reunião - Climbe Cont (6)";
        var evento = new Event().setId("evento-com-sala").setHangoutLink("https://meet.google.com/abc-defg-hij")
                .setAttendees(List.of(new EventAttendee().setEmail("sala@resource.calendar.google.com")
                        .setResource(true).setDisplayName(nome)));
        var resumo = ReuniaoListItemDTO.fromGoogleEventExterno(evento);
        assertThat(resumo.getLocal()).isEqualTo(nome);
        assertThat(resumo.getSalaEmail()).isEqualTo("sala@resource.calendar.google.com");
        assertThat(resumo.getPresencial()).isTrue();
        assertThat(resumo.getLinkMeet()).isEqualTo(evento.getHangoutLink());
        assertThat(resumo.getParticipantes()).isEmpty();
    }
    @Test void preservaInformacoesDoGoogleSemConfundirSalaComParticipante() {
        var evento = new Event().setId("evento-1").setSummary("Reunião externa").setDescription("Pauta completa")
                .setLocation("Sala principal").setHangoutLink("https://meet.google.com/abc-defg-hij")
                .setHtmlLink("https://calendar.google.com/event?eid=teste")
                .setOrganizer(new Event.Organizer().setEmail("ana@example.test").setDisplayName("Ana"))
                .setStart(new EventDateTime().setDateTime(new DateTime("2026-10-19T10:00:00-03:00")))
                .setEnd(new EventDateTime().setDateTime(new DateTime("2026-10-19T11:30:00-03:00")))
                .setAttendees(List.of(new EventAttendee().setEmail("bruno@example.test").setDisplayName("Bruno"),
                        new EventAttendee().setEmail("sala@resource.calendar.google.com").setResource(true)))
                .setAttendeesOmitted(true);

        var resumo = ReuniaoListItemDTO.fromGoogleEventExterno(evento);

        assertThat(resumo.getIdReuniao()).isNegative();
        assertThat(resumo.getOrganizador().nomeCompleto()).isEqualTo("Ana");
        assertThat(resumo.getParticipantes()).extracting(pessoa -> pessoa.email()).containsExactly("bruno@example.test");
        assertThat(resumo.getParticipantesOmitidos()).isTrue();
        assertThat(resumo.getDataHoraFim()).isEqualTo("2026-10-19T11:30:00-03:00");
        assertThat(resumo.getLinkMeet()).isEqualTo(evento.getHangoutLink());
        assertThat(resumo.getGoogleEventUrl()).isEqualTo(evento.getHtmlLink());
        assertThat(resumo.getPauta()).isEqualTo("Pauta completa");
    }

    @Test void aceitaEventoDeDiaInteiroSemListaDeConvidados() {
        var evento = new Event().setId("dia-inteiro").setStart(new EventDateTime().setDate(new DateTime("2026-10-19")));
        var resumo = ReuniaoListItemDTO.fromGoogleEventExterno(evento);
        assertThat(resumo.getDiaInteiro()).isTrue();
        assertThat(resumo.getParticipantes()).isEmpty();
        assertThat(resumo.getOrganizador()).isNull();
        assertThat(resumo.getData()).hasToString("2026-10-19");
    }
}
