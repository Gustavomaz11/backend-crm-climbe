package com.climb.api.service;

import com.climb.api.repository.ParticipanteReuniaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AvisosStreamServiceTest {
    private final SessaoAutenticacaoService sessoes = mock(SessaoAutenticacaoService.class);
    private final ParticipanteReuniaoRepository participantes = mock(ParticipanteReuniaoRepository.class);
    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-10-20T12:00:00Z"));
    private AvisosStreamService service;
    private final List<SseEmitter> emitters = new ArrayList<>();
    private final List<List<String>> mensagens = new ArrayList<>();

    @BeforeEach void configurar() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(i -> agora.get());
        when(clock.millis()).thenAnswer(i -> agora.get().toEpochMilli());
        when(sessoes.estaAtiva(anyString(), anyLong())).thenReturn(true);
        service = spy(new AvisosStreamService(sessoes, participantes, clock));
        doAnswer(i -> {
            var emitter = mock(SseEmitter.class); var eventos = new ArrayList<String>();
            emitters.add(emitter); mensagens.add(eventos);
            doAnswer(envio -> {
                var evento = (SseEmitter.SseEventBuilder) envio.getArgument(0);
                eventos.add(evento.build().stream().map(d -> String.valueOf(d.getData())).collect(Collectors.joining()));
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            return emitter;
        }).when(service).criarEmitter(anyLong());
    }

    @Test void expiracaoDoTokenFechaCanalSemEnviarNovosDados() throws Exception {
        service.conectar(7L, "sessao", agora.get().plusSeconds(10));
        assertThat(mensagens.getFirst()).hasSize(1).allSatisfy(msg -> assertThat(msg).contains("sincronizar"));
        agora.set(agora.get().plusSeconds(11));
        service.alterado(new AvisoAtualizado(7L, null, AvisoAtualizado.Tipo.NOTIFICACOES));
        service.processar();
        verify(emitters.getFirst()).complete();
        assertThat(mensagens.getFirst()).hasSize(1);
        assertThatThrownBy(() -> service.conectar(7L, "sessao", agora.get().minusSeconds(1)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void falhaDeUmaAbaNaoInterrompeEnvioParaOutraERemoveConexao() throws Exception {
        service.conectar(7L, "sessao-a", agora.get().plusSeconds(100));
        service.conectar(7L, "sessao-b", agora.get().plusSeconds(100));
        doThrow(new java.io.IOException("Cliente desconectado")).when(emitters.getFirst()).send(any(SseEmitter.SseEventBuilder.class));
        service.alterado(new AvisoAtualizado(7L, null, AvisoAtualizado.Tipo.NOTIFICACOES)); service.processar();
        assertThat(mensagens.getLast()).anySatisfy(msg -> assertThat(msg).contains("notificacoes"));
        clearInvocations(emitters.getFirst());
        service.alterado(new AvisoAtualizado(7L, null, AvisoAtualizado.Tipo.CONVITES)); service.processar();
        verifyNoInteractions(emitters.getFirst());
    }

    @Test void callbackDeConclusaoLiberaConexaoSemConsultasPeriodicasAoBanco() {
        service.conectar(7L, "sessao", agora.get().plusSeconds(100));
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(emitters.getFirst()).onCompletion(callback.capture()); callback.getValue().run();
        clearInvocations(sessoes);
        service.processar();
        verifyNoInteractions(sessoes, participantes);
    }

    @Test void sessaoRevogadaNaoPodeAbrirCanal() {
        when(sessoes.estaAtiva("revogada", 7L)).thenReturn(false);
        assertThatThrownBy(() -> service.conectar(7L, "revogada", agora.get().plusSeconds(100)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(emitters).isEmpty();
    }

    @Test void encerramentoAtualizaKanbanDeTodosSemDivulgarNotificacoesPrivadas() {
        service.conectar(7L, "sessao-a", agora.get().plusSeconds(100));
        service.conectar(8L, "sessao-b", agora.get().plusSeconds(100));
        service.alterado(new AvisoAtualizado(null, null, AvisoAtualizado.Tipo.KANBAN));
        service.alterado(new AvisoAtualizado(7L, null, AvisoAtualizado.Tipo.NOTIFICACOES));
        service.processar();
        assertThat(mensagens).allSatisfy(eventos -> assertThat(eventos).anySatisfy(msg -> assertThat(msg).contains("kanban")));
        assertThat(mensagens.getFirst()).anySatisfy(msg -> assertThat(msg).contains("notificacoes"));
        assertThat(mensagens.getLast()).noneSatisfy(msg -> assertThat(msg).contains("notificacoes"));
    }
}
