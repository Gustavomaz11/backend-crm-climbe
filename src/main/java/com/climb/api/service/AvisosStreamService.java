package com.climb.api.service;

import com.climb.api.repository.ParticipanteReuniaoRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AvisosStreamService {
    private static final Logger log = LoggerFactory.getLogger(AvisosStreamService.class);
    private static final long DURACAO_MAXIMA_MS = 10 * 60_000;
    private final Map<UUID, Conexao> conexoes = new ConcurrentHashMap<>();
    private final Map<Long, Set<AvisoAtualizado.Tipo>> pendentes = new ConcurrentHashMap<>();
    private final Set<Long> reunioes = ConcurrentHashMap.newKeySet();
    private final SessaoAutenticacaoService sessoes;
    private final ParticipanteReuniaoRepository participantes;
    private final Clock clock;
    private Instant proximoHeartbeat = Instant.EPOCH;
    private record Conexao(Long usuarioId, String sessaoId, Instant expiraEm, SseEmitter emitter) {}

    @org.springframework.beans.factory.annotation.Autowired
    public AvisosStreamService(SessaoAutenticacaoService sessoes, ParticipanteReuniaoRepository participantes) {
        this(sessoes, participantes, Clock.systemUTC());
    }

    AvisosStreamService(SessaoAutenticacaoService sessoes, ParticipanteReuniaoRepository participantes, Clock clock) {
        this.sessoes = sessoes; this.participantes = participantes; this.clock = clock;
    }

    public SseEmitter conectar(Long usuarioId, String sessaoId, Instant expiraEm) {
        if (usuarioId == null || expiraEm == null || !expiraEm.isAfter(clock.instant())
                || !sessoes.estaAtiva(sessaoId, usuarioId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada.");
        }
        Instant limite = clock.instant().plusMillis(DURACAO_MAXIMA_MS);
        Instant encerramento = expiraEm.isBefore(limite) ? expiraEm : limite;
        long duracao = encerramento.toEpochMilli() - clock.millis();
        SseEmitter emitter = criarEmitter(Math.max(1, duracao));
        UUID id = UUID.randomUUID();
        var conexao = new Conexao(usuarioId, sessaoId, encerramento, emitter);
        emitter.onCompletion(() -> conexoes.remove(id));
        emitter.onTimeout(() -> encerrar(id, conexao));
        emitter.onError(erro -> conexoes.remove(id));
        conexoes.put(id, conexao);
        // O cliente consulta o banco após registrar a conexão, cobrindo a janela entre carga inicial e conexão.
        enviar(id, conexao, "sincronizar");
        return emitter;
    }

    protected SseEmitter criarEmitter(long timeout) { return new SseEmitter(timeout); }

    @TransactionalEventListener(fallbackExecution = true)
    public void alterado(AvisoAtualizado evento) {
        if (conexoes.isEmpty()) return; // O histórico permanece no banco para o próximo acesso.
        if (evento.reuniaoId() != null) reunioes.add(evento.reuniaoId());
        if (evento.usuarioId() != null) agendar(evento.usuarioId(), evento.tipo());
        if (evento.usuarioId() == null && evento.reuniaoId() == null && evento.tipo() == AvisoAtualizado.Tipo.KANBAN) {
            conexoes.values().stream().map(Conexao::usuarioId).distinct().forEach(id -> agendar(id, evento.tipo()));
        }
    }

    private void agendar(Long usuarioId, AvisoAtualizado.Tipo tipo) {
        pendentes.merge(usuarioId, Set.of(tipo), (anteriores, novos) -> {
            var tipos = EnumSet.copyOf(anteriores); tipos.addAll(novos); return Set.copyOf(tipos);
        });
    }

    @Scheduled(fixedDelayString = "${app.avisos.intervalo-ms:1000}", scheduler = "avisosScheduler")
    public void processar() {
        if (conexoes.isEmpty()) { pendentes.clear(); reunioes.clear(); return; }
        sincronizarReunioes();
        Map<Long, Set<AvisoAtualizado.Tipo>> lote = new HashMap<>();
        pendentes.forEach((usuario, tipos) -> { if (pendentes.remove(usuario, tipos)) lote.put(usuario, tipos); });
        boolean heartbeat = !clock.instant().isBefore(proximoHeartbeat);
        if (heartbeat) proximoHeartbeat = clock.instant().plusSeconds(20);
        Map<String, Boolean> sessoesAtivas = new HashMap<>();
        conexoes.forEach((id, conexao) -> {
            var tipos = lote.getOrDefault(conexao.usuarioId(), Set.of());
            if (!heartbeat && tipos.isEmpty()) return;
            if (!ativa(conexao, sessoesAtivas)) { encerrar(id, conexao); return; }
            if (heartbeat) enviar(id, conexao, "heartbeat");
            tipos.forEach(tipo -> enviar(id, conexao, tipo.name().toLowerCase(Locale.ROOT)));
        });
    }

    private void sincronizarReunioes() {
        var ids = new HashSet<Long>();
        reunioes.forEach(id -> { if (reunioes.remove(id)) ids.add(id); });
        if (ids.isEmpty()) return;
        try { participantes.buscarUsuarioIdsDasReunioes(ids).forEach(id -> agendar(id, AvisoAtualizado.Tipo.CONVITES)); }
        catch (RuntimeException erro) { reunioes.addAll(ids); log.warn("Sincronização de convites SSE será tentada novamente."); }
    }

    private boolean ativa(Conexao conexao, Map<String, Boolean> verificadas) {
        if (!conexao.expiraEm().isAfter(clock.instant())) return false;
        try { return verificadas.computeIfAbsent(conexao.sessaoId(), sid -> sessoes.estaAtiva(sid, conexao.usuarioId())); }
        catch (RuntimeException erro) { return false; }
    }

    private void enviar(UUID id, Conexao conexao, String tipo) {
        try { conexao.emitter().send(SseEmitter.event().name(tipo).data("{}")); }
        catch (IOException | IllegalStateException erro) { conexoes.remove(id); }
    }

    private void encerrar(UUID id, Conexao conexao) {
        conexoes.remove(id);
        conexao.emitter().complete();
    }

    @PreDestroy
    public void encerrar() { conexoes.forEach(this::encerrar); }
}
