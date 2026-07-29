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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.climb.api.model.Empresa;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Reuniao;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ReuniaoListItemDTO;
import com.climb.api.model.dto.ReuniaoRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.google.api.services.calendar.model.Event;

@Service
public class ReuniaoService {

    private static final Logger log = LoggerFactory.getLogger(ReuniaoService.class);

    private final ReuniaoRepository repository;
    private final EmpresaRepository empresaRepository;
    private final GoogleCalendarService googleCalendarService;
    private final ParticipanteReuniaoRepository participanteReuniaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReuniaoEmailService reuniaoEmailService;
    private final GoogleCredentialService googleCredentialService;

    public ReuniaoService(ReuniaoRepository repository,
                          EmpresaRepository empresaRepository,
                          GoogleCalendarService googleCalendarService,
                          ParticipanteReuniaoRepository participanteReuniaoRepository,
                          UsuarioRepository usuarioRepository,
                          ReuniaoEmailService reuniaoEmailService,
                          GoogleCredentialService googleCredentialService) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.googleCalendarService = googleCalendarService;
        this.participanteReuniaoRepository = participanteReuniaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.reuniaoEmailService = reuniaoEmailService;
        this.googleCredentialService = googleCredentialService;
    }

    public List<ReuniaoListItemDTO> listar(Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        String googleAccessToken = googleCredentialService.obterAccessToken(usuarioId).orElse(null);
        List<Reuniao> reunioes = repository.findVisiveisParaUsuario(usuarioId);
        log.info("ReuniaoService.listar - usuario {} - linhas visiveis no banco: {}", usuarioId, reunioes.size());

        List<Reuniao> visiveis = reunioes;
        if (googleAccessToken != null && !googleAccessToken.isBlank()) {
            visiveis = reunioes.stream()
                    .filter(reuniao -> sincronizarEventoGoogle(reuniao, googleAccessToken))
                    .toList();
            log.info("ReuniaoService.listar - apos sync Google com banco: {} reunioes", visiveis.size());
        }

        List<ReuniaoListItemDTO> resultado = new ArrayList<>(visiveis.stream()
                .map(ReuniaoListItemDTO::fromEntity)
                .toList());

        if (googleAccessToken != null && !googleAccessToken.isBlank()) {
            adicionarEventosExternosGoogle(resultado, visiveis, googleAccessToken);
        }

        resultado.sort(Comparator
                .comparing(ReuniaoListItemDTO::getData, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReuniaoListItemDTO::getHora, Comparator.nullsLast(Comparator.naturalOrder())));
        log.info("ReuniaoService.listar - total resposta filtrada por participantes: {}", resultado.size());
        return resultado;
    }

    private void adicionarEventosExternosGoogle(List<ReuniaoListItemDTO> resultado,
                                                List<Reuniao> reunioes,
                                                String accessToken) {
        Set<String> idsLocais = reunioes.stream()
                .map(Reuniao::getGoogleEventId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));

        try {
            Instant inicio = Instant.now().minus(90, ChronoUnit.DAYS);
            Instant fim = Instant.now().plus(365, ChronoUnit.DAYS);
            googleCalendarService.listarEventosPrimarios(accessToken, inicio, fim).stream()
                    .filter(event -> event != null)
                    .filter(event -> !"cancelled".equalsIgnoreCase(event.getStatus()))
                    .filter(event -> event.getId() != null && !idsLocais.contains(event.getId()))
                    .map(ReuniaoListItemDTO::fromGoogleEventExterno)
                    .forEach(resultado::add);
        } catch (Exception e) {
            log.warn("Falha ao carregar agenda Google do usuario autenticado: {}", e.getMessage());
        }
    }

    public Reuniao buscarPorId(Long id, Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        Reuniao reuniao = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reuniao nao encontrada"));
        exigirParticipante(reuniao.getIdReuniao(), usuarioId);
        return reuniao;
    }

    public List<Reuniao> listarPorEmpresa(Long empresaId, Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        return repository.findByEmpresaVisiveisParaUsuario(empresaId, usuarioId);
    }

    @Transactional
    public Reuniao criar(ReuniaoRequestDTO request, Long usuarioId) throws Exception {
        exigirUsuarioAutenticado(usuarioId);
        String accessToken = googleCredentialService.obterAccessToken(usuarioId).orElse(null);
        Reuniao reuniao = new Reuniao();
        preencherReuniao(reuniao, request);

        Reuniao salva = repository.save(reuniao);
        salvarParticipantes(salva, incluirUsuarioAutenticado(request.getParticipanteIds(), usuarioId));

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
            log.warn("Falha ao criar evento no Google Calendar para reuniao {}: {}", salva.getIdReuniao(), e.getMessage());
        }

        return salva;
    }

    @Transactional
    public Reuniao atualizar(Long id, Long usuarioId, ReuniaoRequestDTO request) {
        exigirUsuarioAutenticado(usuarioId);
        String accessToken = googleCredentialService.obterAccessToken(usuarioId).orElse(null);
        Reuniao reuniao = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reuniao nao encontrada"));
        exigirParticipante(reuniao.getIdReuniao(), usuarioId);
        preencherReuniao(reuniao, request);

        Reuniao salva = repository.save(reuniao);
        if (request.getParticipanteIds() != null) {
            salvarParticipantes(salva, request.getParticipanteIds());
        }

        if (accessToken != null && !accessToken.isBlank() && salva.getGoogleEventId() != null) {
            try {
                googleCalendarService.atualizarEvento(salva, accessToken);
            } catch (Exception e) {
                log.warn("Falha ao atualizar evento no Google Calendar para reuniao {}: {}", salva.getIdReuniao(), e.getMessage());
            }
        }

        return salva;
    }

    public void deletar(Long id, Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        String accessToken = googleCredentialService.obterAccessToken(usuarioId).orElse(null);
        Reuniao reuniao = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reuniao nao encontrada"));
        exigirParticipante(reuniao.getIdReuniao(), usuarioId);
        if (accessToken != null &&
                !accessToken.isBlank() &&
                reuniao.getGoogleEventId() != null &&
                !reuniao.getGoogleEventId().isBlank()) {
            try {
                googleCalendarService.deletarEvento(reuniao.getGoogleEventId(), accessToken);
            } catch (Exception e) {
                log.warn("Falha ao excluir evento no Google Calendar para reuniao {}: {}", reuniao.getIdReuniao(), e.getMessage());
            }
        }
        participanteReuniaoRepository.deleteAll(
                participanteReuniaoRepository.findByReuniao_IdReuniao(reuniao.getIdReuniao()));
        repository.delete(reuniao);
    }

    private void preencherReuniao(Reuniao reuniao, ReuniaoRequestDTO request) {
        if (request.getTitulo() == null || request.getTitulo().isBlank()) {
            throw new RuntimeException("Titulo e obrigatorio");
        }

        if (request.getEmpresaId() == null) {
            throw new RuntimeException("Empresa e obrigatoria");
        }

        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Empresa nao encontrada"));

        reuniao.setTitulo(request.getTitulo());
        reuniao.setEmpresa(empresa);
        reuniao.setData(request.getData());
        reuniao.setHora(request.getHora());
        reuniao.setPresencial(request.getPresencial());
        reuniao.setLocal(request.getLocal());
        reuniao.setPauta(request.getPauta());
        reuniao.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "AGENDADA");
    }

    private void exigirUsuarioAutenticado(Long usuarioId) {
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
    }

    private void exigirParticipante(Long reuniaoId, Long usuarioId) {
        if (!participanteReuniaoRepository.existsByReuniao_IdReuniaoAndUsuario_Id(reuniaoId, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não participa desta reunião");
        }
    }

    private void salvarParticipantes(Reuniao reuniao, List<Long> participanteIds) {
        List<ParticipanteReuniao> atuais = participanteReuniaoRepository.findByReuniao_IdReuniao(reuniao.getIdReuniao());
        if (!atuais.isEmpty()) {
            participanteReuniaoRepository.deleteAll(atuais);
        }

        if (participanteIds == null || participanteIds.isEmpty()) {
            return;
        }

        List<Long> idsUnicos = participanteIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (idsUnicos.isEmpty()) {
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findAllById(idsUnicos);
        List<ParticipanteReuniao> participantes = usuarios.stream().map(usuario -> {
            ParticipanteReuniao participante = new ParticipanteReuniao();
            participante.setReuniao(reuniao);
            participante.setUsuario(usuario);
            return participante;
        }).toList();

        participanteReuniaoRepository.saveAll(participantes);
    }

    private List<Long> incluirUsuarioAutenticado(List<Long> participanteIds, Long usuarioId) {
        List<Long> ids = new ArrayList<>();
        if (participanteIds != null) {
            ids.addAll(participanteIds);
        }
        ids.add(usuarioId);
        return ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private boolean sincronizarEventoGoogle(Reuniao reuniao, String accessToken) {
        if (reuniao.getGoogleEventId() == null || reuniao.getGoogleEventId().isBlank()) {
            return true;
        }

        try {
            if (googleCalendarService.eventoExiste(reuniao.getGoogleEventId(), accessToken)) {
                return true;
            }

            participanteReuniaoRepository.deleteAll(
                    participanteReuniaoRepository.findByReuniao_IdReuniao(reuniao.getIdReuniao()));
            repository.delete(reuniao);
            log.info("Reuniao {} removida localmente porque o evento Google foi excluido", reuniao.getIdReuniao());
            return false;
        } catch (Exception e) {
            log.warn("Falha ao sincronizar evento Google da reuniao {}: {}", reuniao.getIdReuniao(), e.getMessage());
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
