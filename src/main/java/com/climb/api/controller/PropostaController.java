package com.climb.api.controller;

import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.PropostaAprovacaoRequestDTO;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.dto.PropostaResponseDTO;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.service.PropostaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/propostas")
public class PropostaController {

    private final PropostaService service;

    public PropostaController(PropostaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PropostaResponseDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(service.listar()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropostaResponseDTO>> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.buscarPorId(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<PropostaResponseDTO>>> listarPorStatus(@PathVariable PropostaStatus status) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.listarPorStatus(status)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PropostaResponseDTO>> criar(@Valid @RequestBody PropostaRequestDTO proposta) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.criar(proposta)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PropostaResponseDTO>> atualizar(@PathVariable Long id,
                                                                       @Valid @RequestBody PropostaRequestDTO atualizada) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.atualizar(id, atualizada)));
        } catch (RuntimeException e) {
            if ("Proposta não encontrada".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<ApiResponse<PropostaResponseDTO>> aprovar(@PathVariable Long id,
                                                                     @Valid @RequestBody PropostaAprovacaoRequestDTO aprovacao) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.aprovar(id, aprovacao)));
        } catch (RuntimeException e) {
            if ("Proposta não encontrada".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletar(@PathVariable Long id) {
        try {
            service.deletar(id);
            return ResponseEntity.ok(ApiResponse.ok(null, "Proposta removida com sucesso"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }
}