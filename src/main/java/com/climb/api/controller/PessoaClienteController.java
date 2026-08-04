package com.climb.api.controller;

import com.climb.api.model.dto.PessoaClienteRequestDTO;
import com.climb.api.model.dto.PessoaClienteResponseDTO;
import com.climb.api.service.PessoaClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pessoas")
public class PessoaClienteController {
    private final PessoaClienteService service;

    public PessoaClienteController(PessoaClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<PessoaClienteResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public PessoaClienteResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<PessoaClienteResponseDTO> criar(@Valid @RequestBody PessoaClienteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @PutMapping("/{id}")
    public PessoaClienteResponseDTO atualizar(@PathVariable Long id,
                                               @Valid @RequestBody PessoaClienteRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
