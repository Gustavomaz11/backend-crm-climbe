package com.climb.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.climb.api.model.Permissao;
import com.climb.api.service.PermissaoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permissoes")
public class PermissaoController {

    private final PermissaoService service;

    public PermissaoController(PermissaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Permissao> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Permissao buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    @PreAuthorize("@permissaoAcesso.tem('PERMITIR_ACESSO')")
    public Permissao criar(@RequestBody Permissao permissao) {
        return service.criar(permissao);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissaoAcesso.tem('PERMITIR_ACESSO')")
    public Permissao atualizar(@PathVariable Long id, @RequestBody Permissao atualizada) {
        return service.atualizar(id, atualizada);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissaoAcesso.tem('PERMITIR_ACESSO')")
    public void deletar(@PathVariable Long id) {
        service.deletar(id);
    }
}
