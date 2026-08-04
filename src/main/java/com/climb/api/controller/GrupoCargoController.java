package com.climb.api.controller;

import com.climb.api.model.Cargo;
import com.climb.api.model.GrupoCargo;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.CargoGrupoVinculoRequestDTO;
import com.climb.api.model.dto.GrupoCargoRequestDTO;
import com.climb.api.service.GrupoCargoService;
import com.climb.api.service.RbacService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/grupos-cargos")
public class GrupoCargoController {

    private final GrupoCargoService service;
    private final RbacService rbacService;

    public GrupoCargoController(GrupoCargoService service, RbacService rbacService) {
        this.service = service;
        this.rbacService = rbacService;
    }

    @GetMapping
    public List<GrupoCargo> listar() {
        return service.listar();
    }

    @PostMapping
    public GrupoCargo criar(@Valid @RequestBody GrupoCargoRequestDTO request) {
        exigirPermissao();
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public GrupoCargo atualizar(@PathVariable Long id,
                                @Valid @RequestBody GrupoCargoRequestDTO request) {
        exigirPermissao();
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        exigirPermissao();
        service.desativar(id);
    }

    @PutMapping("/cargos/{cargoId}")
    public Cargo vincularCargo(@PathVariable Long cargoId,
                               @RequestBody CargoGrupoVinculoRequestDTO request) {
        exigirPermissao();
        return service.vincularCargo(cargoId, request.grupoId());
    }

    private void exigirPermissao() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Number id)
                || !rbacService.temPermissao(id.longValue(), PermissaoCodigo.CARGO_CRUD)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissao para gerenciar grupos de cargos");
        }
    }
}
