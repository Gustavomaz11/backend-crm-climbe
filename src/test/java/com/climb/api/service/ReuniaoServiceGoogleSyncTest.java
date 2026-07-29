package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Reuniao;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ReuniaoRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.Organizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReuniaoServiceGoogleSyncTest {

    @Mock private ReuniaoRepository reuniaoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private GoogleCalendarService googleCalendarService;
    @Mock private ParticipanteReuniaoRepository participanteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ReuniaoEmailService reuniaoEmailService;
    @Mock private GoogleCredentialService googleCredentialService;

    @Test
    void deveSincronizarComOCredencialDoUsuarioAutenticado() throws Exception {
        when(googleCredentialService.obterAccessToken(7L)).thenReturn(Optional.of("token-google-7"));
        when(reuniaoRepository.findVisiveisParaUsuario(7L)).thenReturn(List.of());
        when(googleCalendarService.listarEventosPrimarios(
                org.mockito.ArgumentMatchers.eq("token-google-7"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        ReuniaoService service = new ReuniaoService(
                reuniaoRepository,
                empresaRepository,
                googleCalendarService,
                participanteRepository,
                usuarioRepository,
                reuniaoEmailService,
                googleCredentialService);

        service.listar(7L);

        verify(googleCredentialService).obterAccessToken(7L);
        verify(googleCalendarService).listarEventosPrimarios(
                org.mockito.ArgumentMatchers.eq("token-google-7"), any(Instant.class), any(Instant.class));
    }

    @Test
    void deveEnviarConfirmacaoSomenteAosUsuariosParticipantes() throws Exception {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(3L);
        empresa.setRazaoSocial("Cliente Externo");
        empresa.setNomeFantasia("Cliente Externo");
        empresa.setEmail("contato@cliente.com");

        Usuario criador = usuario(7L, "Criador", "criador@climbe.com.br");
        Usuario convidado = usuario(8L, "Convidado", "convidado@climbe.com.br");
        ParticipanteReuniao participanteCriador = participante(criador);
        ParticipanteReuniao participanteConvidado = participante(convidado);

        when(googleCredentialService.obterAccessToken(7L)).thenReturn(Optional.of("token-google-7"));
        when(empresaRepository.findById(3L)).thenReturn(Optional.of(empresa));
        when(reuniaoRepository.save(any(Reuniao.class))).thenAnswer(invocation -> {
            Reuniao reuniao = invocation.getArgument(0);
            reuniao.setIdReuniao(11L);
            return reuniao;
        });
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(criador, convidado));
        when(participanteRepository.findByReuniao_IdReuniao(11L))
                .thenReturn(List.of(), List.of(participanteCriador, participanteConvidado));

        Event eventoGoogle = new Event()
                .setId("google-event-11")
                .setOrganizer(new Organizer().setEmail(criador.getEmail()));
        when(googleCalendarService.criarEvento(any(Reuniao.class), eq("token-google-7")))
                .thenReturn(eventoGoogle);
        when(googleCalendarService.extrairLinkMeet(eventoGoogle)).thenReturn("https://meet.google.com/abc-defg-hij");
        when(usuarioRepository.findByEmail(criador.getEmail())).thenReturn(Optional.of(criador));

        ReuniaoRequestDTO request = new ReuniaoRequestDTO();
        request.setTitulo("Reuniao restrita");
        request.setEmpresaId(3L);
        request.setData(LocalDate.of(2026, 7, 30));
        request.setHora(LocalTime.of(10, 0));
        request.setParticipanteIds(List.of(convidado.getId()));

        ReuniaoService service = new ReuniaoService(
                reuniaoRepository,
                empresaRepository,
                googleCalendarService,
                participanteRepository,
                usuarioRepository,
                reuniaoEmailService,
                googleCredentialService);

        service.criar(request, criador.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> destinatariosCaptor = ArgumentCaptor.forClass(Set.class);
        verify(reuniaoEmailService).enviarConfirmacaoCriacao(
                any(Reuniao.class),
                eq("https://meet.google.com/abc-defg-hij"),
                eq(criador.getEmail()),
                eq(criador.getNomeCompleto()),
                destinatariosCaptor.capture());
        assertEquals(Set.of(criador.getEmail(), convidado.getEmail()), destinatariosCaptor.getValue());
    }

    private static Usuario usuario(Long id, String nome, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
        usuario.setEmail(email);
        return usuario;
    }

    private static ParticipanteReuniao participante(Usuario usuario) {
        ParticipanteReuniao participante = new ParticipanteReuniao();
        participante.setUsuario(usuario);
        return participante;
    }
}
