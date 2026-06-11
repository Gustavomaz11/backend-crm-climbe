package com.climb.api.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.climb.api.model.Reuniao;
import com.climb.api.model.dto.ReuniaoListItemDTO;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.google.api.services.calendar.model.Event;

@Service
public class ReuniaoService {

    private static final Logger log = LoggerFactory.getLogger(ReuniaoService.class);

    private final ReuniaoRepository repository;
    private final GoogleCalendarService googleCalendarService;
    private final ParticipanteReuniaoRepository participanteReuniaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReuniaoEmailService reuniaoEmailService;

    public ReuniaoService(ReuniaoRepository repository,
                          GoogleCalendarService googleCalendarService,
                          ParticipanteReuniaoRepository participanteReuniaoRepository,
                          UsuarioRepository usuarioRepository,
                          ReuniaoEmailService reuniaoEmailService) {
        this.repository = repository;
        this.googleCalendarService = googleCalendarService;
        this.participanteReuniaoRepository = participanteReuniaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.reuniaoEmailService = reuniaoEmailService;
    }

    public List<ReuniaoListItemDTO> listar(String googleAccessToken) {
        List<Reuniao> reunioes = repository.findAll();
        log.info("ReuniaoService.listar - linhas no banco: {}", reunioes.size());

        if (googleAccessToken == null || googleAccessToken.isBlank()) {
            List<ReuniaoListItemDTO> soBanco = reunioes.stream().map(ReuniaoListItemDTO::fromEntity).toList();
            log.info("ReuniaoService.listar - sem token Google; só banco: {} DTOs", soBanco.size());
            return soBanco;
        }

        List<Reuniao> filtradas = reunioes.stream()
                .filter(reuniao -> sincronizarEventoGoogle(reuniao, googleAccessToken))
                .toList();
        log.info("ReuniaoService.listar - após sync Google com banco: {} reuniões", filtradas.size());

        Set<String> idsGoogleJaNoClimb = filtradas.stream()
                .map(Reuniao::getGoogleEventId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        log.info("ReuniaoService.listar - googleEventIds já no Climb: {}", idsGoogleJaNoClimb.size());

        List<ReuniaoListItemDTO> resultado = new ArrayList<>(filtradas.stream()
                .map(ReuniaoListItemDTO::fromEntity)
                .toList());
        int antesExternos = resultado.size();

        try {
            Instant min = Instant.now().minus(90, ChronoUnit.DAYS);
            Instant max = Instant.now().plus(365, ChronoUnit.DAYS);
            log.info("ReuniaoService.listar - janela Calendar: {} .. {}", min, max);
            List<Event> externos = googleCalendarService.listarEventosPrimarios(googleAccessToken, min, max);
            int add = 0;
            int skipCancel = 0;
            int skipDup = 0;
            for (Event ev : externos) {
                if (ev == null || "cancelled".equalsIgnoreCase(ev.getStatus())) {
                    skipCancel++;
                    continue;
                }
                String gid = ev.getId();
                if (gid == null || idsGoogleJaNoClimb.contains(gid)) {
                    skipDup++;
                    continue;
                }
                resultado.add(ReuniaoListItemDTO.fromGoogleEventExterno(ev));
                add++;
            }
            log.info("ReuniaoService.listar - Google: {} eventos (pós-filtro serviço); skip cancelados={}; skip dup/id vazio={}; adicionados={}",
                    externos.size(), skipCancel, skipDup, add);
        } catch (Exception e) {
            log.warn("ReuniaoService.listar - falha mescla Calendar: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        resultado.sort(Comparator
                .comparing(ReuniaoListItemDTO::getData, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReuniaoListItemDTO::getHora, Comparator.nullsLast(Comparator.naturalOrder())));
        log.info("ReuniaoService.listar - total resposta: {} ({} do banco + externos)", resultado.size(), antesExternos);
        return resultado;
    }

    public Reuniao buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reunião não encontrada"));
    }

    public List<Reuniao> listarPorEmpresa(Long empresaId) {
        return repository.findByEmpresa_IdEmpresa(empresaId);
    }

    public Reuniao criar(Reuniao reuniao, String accessToken) throws Exception {
        if (reuniao.getTitulo() == null || reuniao.getTitulo().isEmpty()) {
            throw new RuntimeException("Título é obrigatório");
        }

        if (reuniao.getEmpresa() == null) {
            throw new RuntimeException("Empresa é obrigatória");
        }

        Reuniao salva = repository.save(reuniao);

        if (accessToken == null || accessToken.isBlank()) {
            log.info("Reuniao {} criada sem integracao com Google Calendar por ausencia de token", salva.getIdReuniao());
            return salva;
        }

        try {
            Event createdEvent = googleCalendarService.criarEvento(salva, accessToken);
            salva.setGoogleEventId(createdEvent.getId());
            salva = repository.save(salva);
            enviarFeedbackCriacao(salva, createdEvent);
        } catch (Exception e) {
            log.warn("Falha ao criar evento no Google Calendar para reunião {}: {}", salva.getIdReuniao(), e.getMessage());
        }

        return salva;
    }

    public Reuniao atualizar(Long id, Reuniao atualizada, String accessToken) {
        Reuniao reuniao = buscarPorId(id);

        reuniao.setTitulo(atualizada.getTitulo());
        reuniao.setEmpresa(atualizada.getEmpresa());
        reuniao.setData(atualizada.getData());
        reuniao.setHora(atualizada.getHora());
        reuniao.setPresencial(atualizada.getPresencial());
        reuniao.setLocal(atualizada.getLocal());
        reuniao.setPauta(atualizada.getPauta());
        reuniao.setStatus(atualizada.getStatus());

        Reuniao salva = repository.save(reuniao);

        if (accessToken != null && !accessToken.isBlank() && salva.getGoogleEventId() != null) {
            try {
                googleCalendarService.atualizarEvento(salva, accessToken);
            } catch (Exception e) {
                log.warn("Falha ao atualizar evento no Google Calendar para reunião {}: {}", salva.getIdReuniao(), e.getMessage());
            }
        }

        return salva;
    }

    public void deletar(Long id, String accessToken) {
        Reuniao reuniao = buscarPorId(id);
        if (accessToken != null &&
                !accessToken.isBlank() &&
                reuniao.getGoogleEventId() != null &&
                !reuniao.getGoogleEventId().isBlank()) {
            try {
                googleCalendarService.deletarEvento(reuniao.getGoogleEventId(), accessToken);
            } catch (Exception e) {
                log.warn("Falha ao excluir evento no Google Calendar para reunião {}: {}", reuniao.getIdReuniao(), e.getMessage());
            }
        }
        repository.delete(reuniao);
    }

    private boolean sincronizarEventoGoogle(Reuniao reuniao, String accessToken) {
        if (reuniao.getGoogleEventId() == null || reuniao.getGoogleEventId().isBlank()) {
            return true;
        }

        try {
            if (googleCalendarService.eventoExiste(reuniao.getGoogleEventId(), accessToken)) {
                return true;
            }

            repository.delete(reuniao);
            log.info("Reuniao {} removida localmente porque o evento Google foi excluido", reuniao.getIdReuniao());
            return false;
        } catch (Exception e) {
            log.warn("Falha ao sincronizar evento Google da reunião {}: {}", reuniao.getIdReuniao(), e.getMessage());
            return true;
        }
    }

    private void enviarFeedbackCriacao(Reuniao reuniao, Event createdEvent) {
        String linkMeet = googleCalendarService.extrairLinkMeet(createdEvent);
        if (!StringUtils.hasText(linkMeet)) {
            return;
        }

        String emailCriador = resolverEmailCriador(createdEvent);
        String nomeCriador = resolverNomeCriador(emailCriador);

        Set<String> convidados = new LinkedHashSet<>();
        if (reuniao.getEmpresa() != null && StringUtils.hasText(reuniao.getEmpresa().getEmail())) {
            convidados.add(reuniao.getEmpresa().getEmail().trim());
        }

        participanteReuniaoRepository.findByReuniao_IdReuniao(reuniao.getIdReuniao()).forEach(participante -> {
            if (participante.getUsuario() != null && StringUtils.hasText(participante.getUsuario().getEmail())) {
                convidados.add(participante.getUsuario().getEmail().trim());
            }
        });

        reuniaoEmailService.enviarConfirmacaoCriacao(reuniao, linkMeet, emailCriador, nomeCriador, convidados);
    }

    private String resolverEmailCriador(Event createdEvent) {
        if (createdEvent != null && createdEvent.getCreator() != null && StringUtils.hasText(createdEvent.getCreator().getEmail())) {
            return createdEvent.getCreator().getEmail().trim().toLowerCase(Locale.ROOT);
        }

        if (createdEvent != null && createdEvent.getOrganizer() != null && StringUtils.hasText(createdEvent.getOrganizer().getEmail())) {
            return createdEvent.getOrganizer().getEmail().trim().toLowerCase(Locale.ROOT);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && StringUtils.hasText(authentication.getName()) && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return authentication.getName().trim().toLowerCase(Locale.ROOT);
        }

        return null;
    }

    private String resolverNomeCriador(String emailCriador) {
        if (!StringUtils.hasText(emailCriador)) {
            return null;
        }

        return usuarioRepository.findByEmail(emailCriador)
                .map(usuario -> usuario.getNomeCompleto())
                .orElseGet(() -> {
                    int atIndex = emailCriador.indexOf('@');
                    return atIndex > 0 ? emailCriador.substring(0, atIndex) : emailCriador;
                });
    }
}
