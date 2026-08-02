package com.climb.api.controller;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.GrupoPermissaoRequestDTO;
import com.climb.api.model.dto.GrupoPermissaoResponseDTO;
import com.climb.api.service.GrupoPermissaoService;
import com.climb.api.service.RbacService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/grupos-permissoes")
public class GrupoPermissaoController {

    private final GrupoPermissaoService service;
    private final RbacService rbacService;

    public GrupoPermissaoController(GrupoPermissaoService service, RbacService rbacService) {
        this.service = service;
        this.rbacService = rbacService;
    }

    @GetMapping
    public List<GrupoPermissaoResponseDTO> listar() {
        exigirPermissao();
        return service.listar();
    }

    @PostMapping
    public GrupoPermissaoResponseDTO criar(@Valid @RequestBody GrupoPermissaoRequestDTO dto) {
        exigirPermissao();
        return service.criar(dto);
    }

    @PutMapping("/{id}")
    public GrupoPermissaoResponseDTO atualizar(@PathVariable Long id,
                                                @Valid @RequestBody GrupoPermissaoRequestDTO dto) {
        exigirPermissao();
        return service.atualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        exigirPermissao();
        service.desativar(id);
    }

    private void exigirPermissao() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Number id)
                || !rbacService.temPermissao(id.longValue(), PermissaoCodigo.PERMITIR_ACESSO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissao para gerenciar grupos");
        }
    }
}
