package com.climb.api.controller;

import com.climb.api.model.Notificacao;
import com.climb.api.model.dto.NotificacaoRequestDTO;
import com.climb.api.model.dto.NotificacaoResponseDTO;
import com.climb.api.service.NotificacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificacaoResponseDTO> listar() {
        return service.listarDTO();
    }

    @GetMapping("/minhas")
    public List<NotificacaoResponseDTO> listarMinhas(Authentication authentication) {
        return service.listarPorUsuario(usuarioIdAutenticado(authentication));
    }

    @GetMapping("/minhas/nao-lidas")
    public List<NotificacaoResponseDTO> listarMinhasNaoLidas(Authentication authentication) {
        return service.listarNaoLidasPorUsuario(usuarioIdAutenticado(authentication));
    }

    @GetMapping("/minhas/nao-lidas/quantidade")
    public Map<String, Long> contarMinhasNaoLidas(Authentication authentication) {
        long total = service.contarNaoLidasPorUsuario(usuarioIdAutenticado(authentication));
        return Map.of("quantidade", total);
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<NotificacaoResponseDTO> listarPorUsuario(@PathVariable Long usuarioId) {
        return service.listarPorUsuario(usuarioId);
    }

    @GetMapping("/{id}")
    public NotificacaoResponseDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorIdDTO(id);
    }

    @PostMapping
    public NotificacaoResponseDTO criar(@Valid @RequestBody NotificacaoRequestDTO notificacao) {
        return service.criar(notificacao);
    }

    @PostMapping("/usuario/{usuarioId}")
    public NotificacaoResponseDTO criarParaUsuario(@PathVariable Long usuarioId,
                                                   @RequestBody NotificacaoRequestDTO notificacao) {
        return service.criarParaUsuario(usuarioId, notificacao);
    }

    @PutMapping("/{id}")
    public Notificacao atualizarLegado(@PathVariable Long id, @RequestBody Notificacao atualizada) {
        return service.atualizar(id, atualizada);
    }

    @PatchMapping("/{id}")
    public NotificacaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody NotificacaoRequestDTO atualizada) {
        return service.atualizar(id, atualizada);
    }

    @PatchMapping("/{id}/lida")
    public NotificacaoResponseDTO marcarComoLida(@PathVariable Long id) {
        return service.marcarComoLida(id);
    }

    @PatchMapping("/minhas/{id}/lida")
    public NotificacaoResponseDTO marcarMinhaComoLida(@PathVariable Long id, Authentication authentication) {
        return service.marcarComoLidaDoUsuario(id, usuarioIdAutenticado(authentication));
    }

    @PatchMapping("/minhas/lidas")
    public ResponseEntity<Void> marcarMinhasComoLidas(Authentication authentication) {
        service.marcarTodasComoLidasDoUsuario(usuarioIdAutenticado(authentication));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        service.deletar(id);
    }

    private Long usuarioIdAutenticado(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Long usuarioId)) {
            throw new RuntimeException("Usuario autenticado nao identificado");
        }
        return usuarioId;
    }
}
