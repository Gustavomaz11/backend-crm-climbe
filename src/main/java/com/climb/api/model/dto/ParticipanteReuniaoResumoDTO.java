package com.climb.api.model.dto;

import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import java.time.LocalDateTime;
import java.util.Objects;

public record ParticipanteReuniaoResumoDTO(Long id, String nomeCompleto, String email,
                                          RespostaConviteReuniao resposta, LocalDateTime respondidoEm) {
    public static ParticipanteReuniaoResumoDTO from(ParticipanteReuniao p) {
        var u = p.getUsuario();
        boolean organizador = p.getReuniao() != null && Objects.equals(u.getId(), p.getReuniao().getOrganizadorId());
        return new ParticipanteReuniaoResumoDTO(u.getId(), u.getNomeCompleto(), u.getEmail(),
                organizador ? RespostaConviteReuniao.ACEITA : p.getResposta(), p.getRespondidoEm());
    }

    public static ParticipanteReuniaoResumoDTO fromGoogle(Event event, EventAttendee attendee) {
        boolean organizador = Boolean.TRUE.equals(attendee.getOrganizer()) || event.getOrganizer() != null
                && attendee.getEmail() != null && attendee.getEmail().equalsIgnoreCase(event.getOrganizer().getEmail());
        return new ParticipanteReuniaoResumoDTO(null, attendee.getDisplayName(), attendee.getEmail(),
                organizador ? RespostaConviteReuniao.ACEITA : RespostaConviteReuniao.fromGoogle(attendee.getResponseStatus()), null);
    }

    public static ParticipanteReuniaoResumoDTO from(ParticipanteReuniao p, Event event) {
        var local = from(p);
        if (event == null || event.getAttendees() == null || p.getReuniao() != null
                && Objects.equals(p.getUsuario().getId(), p.getReuniao().getOrganizadorId())
                || p.getResposta().definitiva() && !p.isAceiteSincronizadoGoogle()) return local;
        return event.getAttendees().stream().filter(a -> local.email() != null && local.email().equalsIgnoreCase(a.getEmail()))
                .findFirst().map(a -> {
                    var resposta = fromGoogle(event, a).resposta();
                    if (resposta == RespostaConviteReuniao.PENDENTE) return local;
                    return new ParticipanteReuniaoResumoDTO(local.id(), local.nomeCompleto(), local.email(), resposta,
                            resposta == local.resposta() ? local.respondidoEm() : null);
                }).orElse(local);
    }
}
