package com.climb.api.controller;

import com.climb.api.model.dto.ConviteLoteRequestDTO;
import com.climb.api.service.ConviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/convites")
public class ConviteController {
    private final ConviteService service;

    public ConviteController(ConviteService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@permissaoAcesso.tem('PERMITIR_ACESSO')")
    public ConviteService.Resultado convidar(@Valid @RequestBody ConviteLoteRequestDTO request) {
        return service.convidar(request);
    }
}
