package com.climb.api.controller;

import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.repository.ParticipanteReuniaoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/participantes-reuniao")
public class ParticipanteReuniaoController {

    private final ParticipanteReuniaoRepository repository;
    private final com.climb.api.repository.ReuniaoRepository reunioes;

    public ParticipanteReuniaoController(ParticipanteReuniaoRepository repository, com.climb.api.repository.ReuniaoRepository reunioes) {
        this.repository = repository;
        this.reunioes = reunioes;
    }

    @GetMapping
    public List<ParticipanteReuniao> listar() {
        return repository.findVisiveisParaUsuario(getAuthenticatedUserId());
    }

    @GetMapping("/{id}")
    public ParticipanteReuniao buscarPorId(@PathVariable Long id) {
        ParticipanteReuniao participante = repository.findById(id).orElseThrow();
        exigirReuniaoVisivel(participante);
        return participante;
    }

    @PostMapping
    public ParticipanteReuniao criar(@RequestBody ParticipanteReuniao participante) {
        Long reuniaoId = participante.getReuniao() != null ? participante.getReuniao().getIdReuniao() : null;
        exigirReuniaoVisivel(reuniaoId);
        confirmarOrganizador(participante);
        return repository.save(participante);
    }

    @PutMapping("/{id}")
    public ParticipanteReuniao atualizar(@PathVariable Long id, @RequestBody ParticipanteReuniao atualizado) {

        ParticipanteReuniao participante = repository.findById(id).orElseThrow();
        exigirReuniaoVisivel(participante);
        Long novaReuniaoId = atualizado.getReuniao() != null ? atualizado.getReuniao().getIdReuniao() : null;
        exigirReuniaoVisivel(novaReuniaoId);

        if (!java.util.Objects.equals(participante.getReuniao().getIdReuniao(), novaReuniaoId)
                || participante.getUsuario() == null || atualizado.getUsuario() == null
                || !java.util.Objects.equals(participante.getUsuario().getId(), atualizado.getUsuario().getId())) {
            participante.setResposta(com.climb.api.model.enums.RespostaConviteReuniao.PENDENTE);
            participante.setRespondidoEm(null);
            participante.setAceiteSincronizadoGoogle(false);
        }

        participante.setReuniao(atualizado.getReuniao());
        participante.setUsuario(atualizado.getUsuario());
        confirmarOrganizador(participante);

        return repository.save(participante);
    }

    private void confirmarOrganizador(ParticipanteReuniao participante) {
        var reuniao = reunioes.findById(participante.getReuniao().getIdReuniao()).orElseThrow();
        participante.setReuniao(reuniao);
        if (participante.getUsuario() != null && participante.getUsuario().getId() != null
                && participante.getUsuario().getId().equals(reuniao.getOrganizadorId())) {
            participante.setResposta(com.climb.api.model.enums.RespostaConviteReuniao.ACEITA);
            if (participante.getRespondidoEm() == null) participante.setRespondidoEm(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")));
            participante.setAceiteSincronizadoGoogle(true);
        }
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        ParticipanteReuniao participante = repository.findById(id).orElse(null);
        if (participante == null) {
            return;
        }
        exigirReuniaoVisivel(participante);
        repository.delete(participante);
    }

    private void exigirReuniaoVisivel(ParticipanteReuniao participante) {
        Long reuniaoId = participante.getReuniao() != null ? participante.getReuniao().getIdReuniao() : null;
        exigirReuniaoVisivel(reuniaoId);
    }

    private void exigirReuniaoVisivel(Long reuniaoId) {
        if (reuniaoId == null || !repository.existsByReuniao_IdReuniaoAndUsuario_Id(reuniaoId, getAuthenticatedUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não participa desta reunião");
        }
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }

        Object details = auth.getDetails();
        if (details instanceof Long userId) {
            return userId;
        }
        if (details instanceof Integer userId) {
            return userId.longValue();
        }
        if (details instanceof String userId) {
            return Long.parseLong(userId);
        }

        throw new IllegalStateException("Usuário não autenticado");
    }

}
