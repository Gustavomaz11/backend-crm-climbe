package com.climb.api.service;

import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import com.climb.api.repository.ReuniaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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
}
