package com.climb.api.service;

import java.time.LocalDateTime;
import com.climb.api.model.dto.KanbanItemArquivadoDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.climb.api.model.dto.SalaReuniaoDTO;
import com.climb.api.model.dto.UsuarioResumoDTO;
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
    private final GoogleSalaService googleSalaService;

    public ReuniaoService(ReuniaoRepository repository,
                          EmpresaRepository empresaRepository,
                          GoogleCalendarService googleCalendarService,
                          ParticipanteReuniaoRepository participanteReuniaoRepository,
                          UsuarioRepository usuarioRepository,
                          ReuniaoEmailService reuniaoEmailService,
                          GoogleCredentialService googleCredentialService,
                          GoogleSalaService googleSalaService) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.googleCalendarService = googleCalendarService;
        this.participanteReuniaoRepository = participanteReuniaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.reuniaoEmailService = reuniaoEmailService;
        this.googleCredentialService = googleCredentialService;
        this.googleSalaService = googleSalaService;
    }

    public List<SalaReuniaoDTO> listarSalas(Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        return googleSalaService.listar(exigirCredencialGoogle(usuarioId));
    }

    public List<ReuniaoListItemDTO> listar(Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        String googleAccessToken = googleCredentialService.obterAccessToken(usuarioId).orElse(null);
        List<Reuniao> reunioes = repository.findVisiveisParaUsuario(usuarioId);
        log.info("ReuniaoService.listar - usuario {} - linhas visiveis no banco: {}", usuarioId, reunioes.size());

        List<Reuniao> visiveis = reunioes;
        if (googleAccessToken != null && !googleAccessToken.isBlank()) {
            visiveis = reunioes.stream()
                    .filter(reuniao -> !java.util.Objects.equals(reuniao.getOrganizadorId(), usuarioId)
                            || sincronizarEventoGoogle(reuniao, googleAccessToken))
                    .toList();
            log.info("ReuniaoService.listar - apos sync Google com banco: {} reunioes", visiveis.size());
        }

        List<ReuniaoListItemDTO> resultado = new ArrayList<>(visiveis.stream()
                .map(ReuniaoListItemDTO::fromEntity)
                .toList());

        if (googleAccessToken != null && !googleAccessToken.isBlank()) {
            adicionarEventosExternosGoogle(resultado, googleAccessToken);
        }

        resultado.sort(Comparator
                .comparing(ReuniaoListItemDTO::getData, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReuniaoListItemDTO::getHora, Comparator.nullsLast(Comparator.naturalOrder())));
        log.info("ReuniaoService.listar - total resposta filtrada por participantes: {}", resultado.size());
        return resultado;
    }

    private void adicionarEventosExternosGoogle(List<ReuniaoListItemDTO> resultado,
                                                String accessToken) {
        var resumosLocais = resultado.stream().filter(resumo -> StringUtils.hasText(resumo.getGoogleEventId()))
                .collect(Collectors.toMap(ReuniaoListItemDTO::getGoogleEventId, resumo -> resumo, (primeiro, outro) -> primeiro));

        try {
            Instant inicio = Instant.now().minus(90, ChronoUnit.DAYS);
            Instant fim = Instant.now().plus(365, ChronoUnit.DAYS);
            googleCalendarService.listarEventosPrimarios(accessToken, inicio, fim).stream()
                    .filter(event -> event != null)
                    .filter(event -> !"cancelled".equalsIgnoreCase(event.getStatus()))
                    .filter(event -> event.getId() != null)
                    .forEach(event -> {
                        var resumoLocal = resumosLocais.get(event.getId());
                        if (resumoLocal != null) {
                            resumoLocal.aplicarSalaFisicaGoogle(event);
                            return;
                        }
                        var resumo = ReuniaoListItemDTO.fromGoogleEventExterno(event);
                        resumo.setLinkMeet(googleCalendarService.extrairLinkMeet(event));
                        resultado.add(resumo);
                    });
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

    @Transactional(readOnly = true)
    public ReuniaoListItemDTO buscarDetalhes(Long id, Long usuarioId) {
        Reuniao reuniao = buscarPorId(id, usuarioId);
        var detalhes = ReuniaoListItemDTO.fromEntity(reuniao);
        if (StringUtils.hasText(reuniao.getGoogleEventId())) {
            googleCredentialService.obterAccessToken(usuarioId).ifPresent(token -> {
                try {
                    detalhes.aplicarSalaFisicaGoogle(googleCalendarService.buscarEvento(reuniao.getGoogleEventId(), token));
                } catch (Exception e) {
                    log.warn("Não foi possível consultar a sala física da reunião {} no Google Agenda", id);
                }
            });
        }
        detalhes.setParticipantes(participanteReuniaoRepository.findByReuniao_IdReuniao(id).stream()
                .map(ParticipanteReuniao::getUsuario).filter(java.util.Objects::nonNull)
                .map(usuario -> new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail())).toList());
        if (reuniao.getOrganizadorId() != null) {
            usuarioRepository.findById(reuniao.getOrganizadorId()).ifPresent(usuario -> detalhes.setOrganizador(
                    new UsuarioResumoDTO(usuario.getId(), usuario.getNomeCompleto(), usuario.getEmail())));
        }
        return detalhes;
    }

    @Transactional(readOnly = true)
    public List<KanbanItemArquivadoDTO> listarArquivados(Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        return repository.findVisiveisParaUsuario(usuarioId).stream().filter(Reuniao::estaArquivado)
                .map(r -> new KanbanItemArquivadoDTO(r.getIdReuniao(), r.getTitulo(),
                        "Realizado", null, r.getArquivadoEm(), ReuniaoListItemDTO.fromEntity(r))).toList();
    }

    @Transactional
    public void alterarArquivamento(Long reuniaoId, Long usuarioId, boolean arquivado) {
        Reuniao reuniao = buscarPorId(reuniaoId, usuarioId);
        reuniao.alterarArquivamento(arquivado, LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")));
        repository.save(reuniao);
    }

    public List<Reuniao> listarPorEmpresa(Long empresaId, Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        return repository.findByEmpresaVisiveisParaUsuario(empresaId, usuarioId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Reuniao criar(ReuniaoRequestDTO request, Long usuarioId) throws Exception {
        exigirUsuarioAutenticado(usuarioId);
        String accessToken = exigirCredencialGoogle(usuarioId);
        Reuniao reuniao = new Reuniao();
        preencherReuniao(reuniao, request, accessToken);
        reuniao.setOrganizadorId(usuarioId);

        Reuniao salva = repository.save(reuniao);
        List<Usuario> convidados = salvarParticipantes(salva, incluirUsuarioAutenticado(request.getParticipanteIds(), usuarioId));

        try {
            Event createdEvent = googleCalendarService.criarEvento(salva, accessToken, emailsConvidados(convidados));
            salva.setGoogleEventId(createdEvent.getId());
            salva.setLinkMeet(googleCalendarService.extrairLinkMeet(createdEvent));
            salva = repository.save(salva);
        } catch (Exception e) {
            log.warn("Falha ao criar evento no Google Calendar para reuniao {}: {}", salva.getIdReuniao(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível agendar no Google Agenda. Confira a conexão com o Google e tente novamente.", e);
        }
        reuniaoEmailService.notificarAgendamento(salva, convidados);
        return salva;
    }

    @Transactional
    public Reuniao atualizar(Long id, Long usuarioId, ReuniaoRequestDTO request) {
        exigirUsuarioAutenticado(usuarioId);
        Reuniao reuniao = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reuniao nao encontrada"));
        exigirParticipante(reuniao.getIdReuniao(), usuarioId);
        Long organizadorId = reuniao.getOrganizadorId() == null ? usuarioId : reuniao.getOrganizadorId();
        String accessToken = exigirCredencialGoogle(organizadorId);
        String agendamentoAnterior = assinaturaAgendamento(reuniao);
        Set<Long> convidadosAnteriores = participanteReuniaoRepository.findByReuniao_IdReuniao(id).stream()
                .map(p -> p.getUsuario().getId()).collect(Collectors.toSet());
        preencherReuniao(reuniao, request, accessToken);

        Reuniao salva = repository.save(reuniao);
        List<Usuario> convidados = salvarParticipantes(salva, incluirUsuarioAutenticado(
                request.getParticipanteIds() == null ? new ArrayList<>(convidadosAnteriores) : request.getParticipanteIds(), organizadorId));

        try {
            Event evento = salva.getGoogleEventId() == null
                    ? googleCalendarService.criarEvento(salva, accessToken, emailsConvidados(convidados))
                    : googleCalendarService.atualizarEvento(salva, accessToken, emailsConvidados(convidados));
            salva.setGoogleEventId(evento.getId());
            salva.setLinkMeet(googleCalendarService.extrairLinkMeet(evento));
            repository.save(salva);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível atualizar a reunião no Google Agenda. Tente novamente.", e);
        }
        if (!agendamentoAnterior.equals(assinaturaAgendamento(salva))) {
            reuniaoEmailService.notificarAgendamento(salva, convidados);
        } else {
            List<Usuario> novos = convidados.stream().filter(u -> !convidadosAnteriores.contains(u.getId())).toList();
            if (!novos.isEmpty()) reuniaoEmailService.notificarAgendamento(salva, novos);
        }
        return salva;
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        exigirUsuarioAutenticado(usuarioId);
        Reuniao reuniao = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reuniao nao encontrada"));
        exigirParticipante(reuniao.getIdReuniao(), usuarioId);
        String accessToken = exigirCredencialGoogle(reuniao.getOrganizadorId() == null ? usuarioId : reuniao.getOrganizadorId());
        if (accessToken != null &&
                !accessToken.isBlank() &&
                reuniao.getGoogleEventId() != null &&
                !reuniao.getGoogleEventId().isBlank()) {
            try {
                googleCalendarService.deletarEvento(reuniao.getGoogleEventId(), accessToken);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível cancelar no Google Agenda. Tente novamente.", e);
            }
        }
        participanteReuniaoRepository.deleteAll(
                participanteReuniaoRepository.findByReuniao_IdReuniao(reuniao.getIdReuniao()));
        repository.delete(reuniao);
    }

    private void preencherReuniao(Reuniao reuniao, ReuniaoRequestDTO request, String accessToken) {
        if (request.getTitulo() == null || request.getTitulo().isBlank()) {
            throw new RuntimeException("Titulo e obrigatorio");
        }

        Empresa empresa = request.getEmpresaId() == null ? null : empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new RuntimeException("Empresa nao encontrada"));

        if (request.getData() == null || request.getHora() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data e o horário da reunião.");
        }
        String salaEmail = Boolean.TRUE.equals(request.getPresencial()) && StringUtils.hasText(request.getSalaEmail())
                ? request.getSalaEmail().trim() : null;
        String local = request.getLocal();
        if (salaEmail != null) {
            boolean manterLocal = salaEmail.equalsIgnoreCase(reuniao.getSalaEmail())
                    && java.util.Objects.equals(local, reuniao.getLocal());
            local = manterLocal ? reuniao.getLocal()
                    : googleSalaService.listar(accessToken).stream()
                        .filter(sala -> sala.email().equalsIgnoreCase(salaEmail)).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "A sala selecionada não está disponível no catálogo do Google.")).nome();
        }
        if (Boolean.TRUE.equals(request.getPresencial()) && !StringUtils.hasText(local)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o local da reunião presencial.");
        }
        reuniao.setTitulo(request.getTitulo());
        reuniao.setEmpresa(empresa);
        reuniao.setData(request.getData());
        reuniao.setHora(request.getHora());
        reuniao.setPresencial(Boolean.TRUE.equals(request.getPresencial()));
        reuniao.setLocal(local);
        reuniao.setSalaEmail(salaEmail);
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

    private List<Usuario> salvarParticipantes(Reuniao reuniao, List<Long> participanteIds) {
        List<Long> ids = participanteIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<Usuario> usuarios = new ArrayList<>(usuarioRepository.findAllById(ids));
        if (usuarios.size() != ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um dos convidados não foi encontrado.");
        usuarioRepository.findByEmail(GoogleCalendarService.EMAIL_OBRIGATORIO).ifPresent(contato -> {
            if (usuarios.stream().noneMatch(u -> u.getId().equals(contato.getId()))) usuarios.add(contato);
        });
        List<ParticipanteReuniao> atuais = participanteReuniaoRepository.findByReuniao_IdReuniao(reuniao.getIdReuniao());
        participanteReuniaoRepository.deleteAll(atuais);
        participanteReuniaoRepository.flush();
        participanteReuniaoRepository.saveAll(usuarios.stream().map(usuario -> {
            ParticipanteReuniao participante = new ParticipanteReuniao();
            participante.setReuniao(reuniao);
            participante.setUsuario(usuario);
            return participante;
        }).toList());
        return usuarios;
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

    private String exigirCredencialGoogle(Long usuarioId) {
        return googleCredentialService.obterAccessToken(usuarioId).filter(StringUtils::hasText)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Entre com sua conta Google e autorize o Google Agenda para agendar esta reunião."));
    }

    private List<String> emailsConvidados(List<Usuario> usuarios) {
        return usuarios.stream().map(Usuario::getEmail).filter(StringUtils::hasText).distinct().toList();
    }

    private String assinaturaAgendamento(Reuniao reuniao) {
        return String.join("|", String.valueOf(reuniao.getTitulo()), String.valueOf(reuniao.getData()),
                String.valueOf(reuniao.getHora()), String.valueOf(reuniao.getPresencial()),
                String.valueOf(reuniao.getLocal()), String.valueOf(reuniao.getSalaEmail()), String.valueOf(reuniao.getLinkMeet()));
    }
}
