package com.climb.api.service;

import com.climb.api.model.ContratoApoioAtuacao;
import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.Usuario;
import com.climb.api.repository.ContratoApoioAtuacaoRepository;
import com.climb.api.repository.ContratoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class ContratoApoioAtuacaoService {
    private final ContratoApoioAtuacaoRepository atuacoes;
    private final ContratoRepository contratos;
    private final Clock clock;

    @Autowired
    public ContratoApoioAtuacaoService(ContratoApoioAtuacaoRepository atuacoes, ContratoRepository contratos) {
        this(atuacoes, contratos, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }
    ContratoApoioAtuacaoService(ContratoApoioAtuacaoRepository atuacoes, ContratoRepository contratos, Clock clock) {
        this.atuacoes = atuacoes; this.contratos = contratos; this.clock = clock;
    }

    @Transactional
    public void sincronizar(ContratoKanbanTask tarefa) {
        bloquearContrato(tarefa);
        LocalDateTime agora = LocalDateTime.now(clock);
        Map<Long, Usuario> selecionados = new HashMap<>();
        if (!tarefa.getRaia().isConcluiTarefas()) {
            Set<Long> atribuidos = new HashSet<>();
            tarefa.getResponsaveisEfetivos().forEach(u -> atribuidos.add(u.getId()));
            tarefa.getApoios().stream().filter(u -> atribuidos.contains(u.getId()))
                    .forEach(u -> selecionados.put(u.getId(), u));
        }
        var abertos = atuacoes.findByTarefa_IdTaskAndFimIsNull(tarefa.getIdTask());
        Set<Long> emAtuacao = new HashSet<>();
        for (var periodo : abertos) {
            Long usuarioId = periodo.getUsuario().getId();
            if (selecionados.containsKey(usuarioId)) emAtuacao.add(usuarioId);
            else { periodo.setFim(agora); atuacoes.save(periodo); }
        }
        selecionados.forEach((id, usuario) -> {
            if (emAtuacao.contains(id)) return;
            var periodo = new ContratoApoioAtuacao(); periodo.setContrato(tarefa.getContrato());
            periodo.setTarefa(tarefa); periodo.setUsuario(usuario); periodo.setInicio(agora);
            atuacoes.save(periodo);
        });
    }

    @Transactional
    public void encerrar(ContratoKanbanTask tarefa) {
        bloquearContrato(tarefa);
        LocalDateTime agora = LocalDateTime.now(clock);
        atuacoes.findByTarefa_IdTaskAndFimIsNull(tarefa.getIdTask()).forEach(p -> {
            p.setFim(agora); atuacoes.save(p);
        });
    }

    @Transactional
    public void desvincular(ContratoKanbanTask tarefa) {
        atuacoes.findByTarefa_IdTask(tarefa.getIdTask()).forEach(p -> {
            p.setTarefa(null); atuacoes.save(p);
        });
        atuacoes.flush();
    }

    private void bloquearContrato(ContratoKanbanTask tarefa) {
        contratos.findByIdForUpdate(tarefa.getContrato().getIdContrato()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrato não encontrado."));
    }
}
