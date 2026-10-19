package com.climb.api.service;

import com.climb.api.model.Reuniao;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ReuniaoDisponibilidadeDTO;
import com.climb.api.model.dto.ReuniaoDisponibilidadeRequestDTO;
import com.climb.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReuniaoDisponibilidadeService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final ReuniaoRepository reunioes;
    private final ParticipanteReuniaoRepository participantes;
    private final UsuarioRepository usuarios;
    private final GoogleDisponibilidadeService google;

    public ReuniaoDisponibilidadeService(ReuniaoRepository reunioes, ParticipanteReuniaoRepository participantes,
                                         UsuarioRepository usuarios, GoogleDisponibilidadeService google) {
        this.reunioes = reunioes;
        this.participantes = participantes;
        this.usuarios = usuarios;
        this.google = google;
    }

    public static LocalDateTime fim(LocalDate data, LocalTime hora, LocalTime horaFim) {
        if (data == null || hora == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Informe a data e o horário de início da reunião.");
        var inicio = data.atTime(hora);
        var fim = horaFim == null ? inicio.plusHours(1) : data.atTime(horaFim);
        if (!fim.isAfter(inicio)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "O horário de término deve ser posterior ao início da reunião.");
        return fim;
    }

    public ReuniaoDisponibilidadeDTO consultar(ReuniaoDisponibilidadeRequestDTO request, Reuniao editando, String token) {
        var fim = fim(request.data(), request.hora(), request.horaFim());
        return consultar(request.data().atTime(request.hora()), fim, editando,
                request.salaEmails(), request.participanteIds(), request.local(), token);
    }

    public void exigirLocalDisponivel(Reuniao reuniao, String token) {
        if (!Boolean.TRUE.equals(reuniao.getPresencial()) || "CANCELADA".equals(reuniao.getStatus())
                || "CANCELADO".equals(reuniao.getStatus())) return;
        boolean sala = StringUtils.hasText(reuniao.getSalaEmail());
        var resultado = consultar(reuniao.getData().atTime(reuniao.getHora()), reuniao.getDataHoraFim(), reuniao,
                sala ? List.of(reuniao.getSalaEmail()) : List.of(), List.of(), reuniao.getLocal(), token);
        Boolean disponivel = sala ? resultado.salas().getFirst().disponivel() : resultado.localDisponivel();
        if (Boolean.FALSE.equals(resultado.localDisponivel())) disponivel = false;
        if (Boolean.FALSE.equals(disponivel)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A sala/local está ocupada durante o período selecionado. Escolha outro local ou horário.");
        if (disponivel == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Não foi possível verificar a disponibilidade da sala. Tente novamente.");
    }

    private ReuniaoDisponibilidadeDTO consultar(LocalDateTime inicio, LocalDateTime fim, Reuniao editando,
                                                List<String> salaEmails, List<Long> usuarioIds, String local, String token) {
        var conflitos = reunioes.findByDataBetween(inicio.toLocalDate().minusDays(1), fim.toLocalDate()).stream()
                .filter(r -> editando == null || !Objects.equals(r.getIdReuniao(), editando.getIdReuniao()))
                .filter(r -> !"CANCELADA".equals(r.getStatus()) && !"CANCELADO".equals(r.getStatus()))
                .filter(r -> r.getData() != null && r.getHora() != null)
                .filter(r -> r.getData().atTime(r.getHora()).isBefore(fim) && r.getDataHoraFim().isAfter(inicio)).toList();
        var idsOcupados = conflitos.isEmpty() ? Set.<Long>of() : participantes.findByReuniaoIn(conflitos).stream()
                .map(p -> p.getUsuario().getId()).collect(Collectors.toSet());
        var listaUsuarios = usuarioIds == null ? List.<Usuario>of() : usuarios.findAllById(usuarioIds);
        var salas = salaEmails == null ? List.<String>of() : salaEmails.stream().filter(StringUtils::hasText)
                .map(ReuniaoDisponibilidadeService::normalizar).distinct().toList();
        var emails = new LinkedHashSet<>(salas);
        listaUsuarios.stream().map(Usuario::getEmail).filter(StringUtils::hasText)
                .map(ReuniaoDisponibilidadeService::normalizar).forEach(emails::add);
        var agendas = google.consultar(token, new ArrayList<>(emails), inicio.atZone(ZONE).toInstant(),
                fim.atZone(ZONE).toInstant(), editando == null ? null : editando.getGoogleEventId());
        var salasResultado = salas.stream().map(email -> new ReuniaoDisponibilidadeDTO.Sala(email,
                conflitos.stream().anyMatch(r -> Boolean.TRUE.equals(r.getPresencial()) && email.equals(normalizar(r.getSalaEmail())))
                        ? Boolean.FALSE : agendas.get(email))).toList();
        var pessoasResultado = listaUsuarios.stream().map(usuario -> new ReuniaoDisponibilidadeDTO.Participante(usuario.getId(),
                idsOcupados.contains(usuario.getId()) || conflitos.stream().anyMatch(r -> usuario.getId().equals(r.getOrganizadorId()))
                        ? Boolean.FALSE : agendas.get(normalizar(usuario.getEmail())))).toList();
        Boolean localDisponivel = !StringUtils.hasText(local) ? null : conflitos.stream()
                .noneMatch(r -> Boolean.TRUE.equals(r.getPresencial()) && normalizar(local).equals(normalizar(r.getLocal())));
        return new ReuniaoDisponibilidadeDTO(salasResultado, pessoasResultado, localDisponivel);
    }

    private static String normalizar(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
