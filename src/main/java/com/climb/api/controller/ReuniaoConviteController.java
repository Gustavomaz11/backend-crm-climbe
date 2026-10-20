package com.climb.api.controller;

import com.climb.api.model.dto.ReuniaoListItemDTO;
import com.climb.api.service.ReuniaoConviteRespostaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/reunioes")
public class ReuniaoConviteController {
    private final ReuniaoConviteRespostaService service;

    public ReuniaoConviteController(ReuniaoConviteRespostaService service) { this.service = service; }

    @GetMapping("/convites/pendentes")
    public List<ReuniaoListItemDTO> listarPendentes(Authentication auth) {
        return service.listarPendentes(usuarioId(auth));
    }

    @PostMapping("/{id}/aceitar")
    public ReuniaoListItemDTO aceitar(@PathVariable Long id, Authentication auth) {
        return service.aceitar(id, usuarioId(auth));
    }

    @PostMapping("/{id}/rejeitar")
    public ReuniaoListItemDTO rejeitar(@PathVariable Long id, Authentication auth) {
        return service.rejeitar(id, usuarioId(auth));
    }

    private Long usuarioId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Number id) return id.longValue();
        if (auth != null && auth.getDetails() instanceof String id) return Long.parseLong(id);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
    }
}
