package com.climb.api.controller;

import com.climb.api.model.dto.EmpresaRequestDTO;
import com.climb.api.model.dto.EmpresaResponseDTO;
import com.climb.api.service.EmpresaService;
import com.climb.api.service.EmpresaFinanceiroService;
import com.climb.api.model.dto.EmpresaFinanceiroResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/empresas")
public class EmpresaController {

    private final EmpresaService service;
    private final EmpresaFinanceiroService financeiroService;

    public EmpresaController(EmpresaService service, EmpresaFinanceiroService financeiroService) {
        this.service = service;
        this.financeiroService = financeiroService;
    }

    @GetMapping
    public ResponseEntity<List<EmpresaResponseDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @GetMapping("/{id}/financeiro")
    public ResponseEntity<EmpresaFinanceiroResponseDTO> consultarFinanceiro(@PathVariable Long id) {
        return ResponseEntity.ok(financeiroService.consultar(id));
    }

    @PostMapping
    public ResponseEntity<EmpresaResponseDTO> criar(@Valid @RequestBody EmpresaRequestDTO empresaRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(empresaRequestDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody EmpresaRequestDTO empresaRequestDto) {
        return ResponseEntity.ok(service.atualizar(id, empresaRequestDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
