package com.climb.api.model.dto;

import com.climb.api.model.Reuniao;
import com.google.api.services.calendar.model.Event;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class ReuniaoListItemDTO {

    private Long idReuniao;
    private String titulo;
    /** ISO-8601 com offset (ex.: America/Fortaleza) — mesmo contrato do front em feature/integration-pages. */
    private String dataHora;
    private LocalDate data;
    private LocalTime hora;
    private Long empresaId;
    private EmpresaResumoDTO empresa;
    private String local;
    private String salaEmail;
    private Boolean presencial;
    private String pauta;
    private String status;
    private java.time.LocalDateTime arquivadoEm;
    private String googleEventId;
    private String linkMeet;
    private String dataHoraFim;
    private String googleEventUrl;
    private UsuarioResumoDTO organizador;
    private List<UsuarioResumoDTO> participantes;
    private Boolean participantesOmitidos;
    private com.climb.api.model.enums.RespostaConviteReuniao minhaResposta;
    private Long conviteId;
    private java.time.LocalDateTime respondidoEm;
    /** true quando o Google envia só {@code start.date} (evento de dia inteiro). */
    private Boolean diaInteiro;

    private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");

    public Boolean getPresencial() {
        return (local != null && !local.isBlank()) || Boolean.TRUE.equals(presencial);
    }

    public static ReuniaoListItemDTO fromEntity(Reuniao r) {
        ReuniaoListItemDTO d = new ReuniaoListItemDTO();
        d.setIdReuniao(r.getIdReuniao());
        d.setTitulo(r.getTitulo());
        d.setData(r.getData());
        d.setHora(r.getHora());
        if (r.getEmpresa() != null) {
            d.setEmpresaId(r.getEmpresa().getIdEmpresa());
            d.setEmpresa(EmpresaResumoDTO.from(r.getEmpresa()));
        }
        d.setLocal(r.getLocal());
        d.setSalaEmail(r.getSalaEmail());
        d.setPresencial(r.getPresencial());
        d.setPauta(r.getPauta());
        d.setStatus(r.getStatus());
        d.setArquivadoEm(r.getArquivadoEm());
        d.setGoogleEventId(r.getGoogleEventId());
        d.setLinkMeet(r.getLinkMeet());
        d.setDiaInteiro(false);
        d.setDataHora(buildDataHoraIso(r.getData(), r.getHora()));
        if (r.getData() != null && r.getHora() != null) {
            d.setDataHoraFim(r.getDataHoraFim().atZone(ZONE)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        return d;
    }

    /**
     * Eventos criados só no Google Calendar (sem linha no Climb). ID sintético negativo para não colidir com PK do banco.
     */
    public static ReuniaoListItemDTO fromGoogleEventExterno(Event event) {
        ReuniaoListItemDTO d = new ReuniaoListItemDTO();
        d.setIdReuniao(syntheticId(event.getId()));
        d.setTitulo(event.getSummary() != null ? event.getSummary() : "(Sem título)");
        d.setGoogleEventId(event.getId());
        d.setGoogleEventUrl(event.getHtmlLink());
        d.setLinkMeet(event.getHangoutLink());
        if (event.getEnd() != null && event.getEnd().getDateTime() != null) {
            d.setDataHoraFim(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(
                    event.getEnd().getDateTime().getValue()), ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (event.getOrganizer() != null) {
            d.setOrganizador(new UsuarioResumoDTO(null, event.getOrganizer().getDisplayName(), event.getOrganizer().getEmail()));
        }
        d.setParticipantes(event.getAttendees() == null ? List.of() : event.getAttendees().stream()
                .filter(convidado -> !SalaReuniaoDTO.isResource(convidado))
                .map(convidado -> new UsuarioResumoDTO(null, convidado.getDisplayName(), convidado.getEmail())).toList());
        d.setParticipantesOmitidos(Boolean.TRUE.equals(event.getAttendeesOmitted()));
        boolean diaInteiro =
                event.getStart() != null
                        && event.getStart().getDate() != null
                        && event.getStart().getDateTime() == null;
        d.setDiaInteiro(diaInteiro);

        ZonedDateTime inicio = instantInicio(event);
        if (inicio != null) {
            d.setData(inicio.toLocalDate());
            d.setHora(inicio.toLocalTime().withNano(0));
            d.setDataHora(inicio.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        d.setEmpresaId(0L);
        d.setEmpresa(null);
        d.setLocal(event.getLocation());
        boolean virtual =
                (event.getHangoutLink() != null && !event.getHangoutLink().isBlank())
                        || (event.getConferenceData() != null
                                && event.getConferenceData().getEntryPoints() != null
                                && !event.getConferenceData().getEntryPoints().isEmpty());
        d.setPresencial(!virtual);
        d.aplicarSalaFisicaGoogle(event);
        d.setPauta(event.getDescription());
        d.setStatus("GOOGLE_CALENDAR");
        return d;
    }

    public void aplicarSalaFisicaGoogle(Event event) {
        var salasFisicas = SalaReuniaoDTO.fromGoogleEvent(event);
        if (salasFisicas.isEmpty()) return;
        setLocal(salasFisicas.stream().map(SalaReuniaoDTO::nome).collect(java.util.stream.Collectors.joining(", ")));
        setSalaEmail(salasFisicas.getFirst().email());
        setPresencial(true);
    }

    private static long syntheticId(String googleEventId) {
        long h = (long) googleEventId.hashCode();
        if (h >= 0) {
            return -h - 1L;
        }
        return h;
    }

    private static ZonedDateTime instantInicio(Event event) {
        if (event.getStart() == null) {
            return null;
        }
        if (event.getStart().getDateTime() != null) {
            long ms = event.getStart().getDateTime().getValue();
            return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZONE);
        }
        if (event.getStart().getDate() != null) {
            String raw = event.getStart().getDate().toString();
            if (raw != null && raw.length() >= 10) {
                LocalDate day = LocalDate.parse(raw.substring(0, 10));
                return day.atStartOfDay(ZONE);
            }
        }
        return null;
    }

    private static String buildDataHoraIso(LocalDate data, LocalTime hora) {
        if (data == null || hora == null) {
            return null;
        }
        ZonedDateTime zdt = ZonedDateTime.of(data, hora, ZONE);
        return zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
