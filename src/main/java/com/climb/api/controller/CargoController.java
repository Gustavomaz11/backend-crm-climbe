package com.climb.api.controller;

import com.climb.api.model.Cargo;
import com.climb.api.service.CargoService;
import com.climb.api.service.RbacService;
import com.climb.api.model.PermissaoCodigo;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cargos")
public class CargoController {

    private final CargoService service;
    private final RbacService rbacService;

    public CargoController(CargoService service, RbacService rbacService) {
        this.service = service;
        this.rbacService = rbacService;
    }

    @GetMapping
    public List<Cargo> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Cargo buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PostMapping
    public Cargo criar(@RequestBody Cargo cargo) {
        exigirPermissao();
        return service.criar(cargo);
    }

    @PutMapping("/{id}")
    public Cargo atualizar(@PathVariable Long id, @RequestBody Cargo atualizado) {
        exigirPermissao();
        return service.atualizar(id, atualizado);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        exigirPermissao();
        service.deletar(id);
    }

    private void exigirPermissao() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Number id)
                || !rbacService.temPermissao(id.longValue(), PermissaoCodigo.CARGO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissao para gerenciar cargos");
        }
    }
}
