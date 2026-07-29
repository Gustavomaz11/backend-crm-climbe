package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineCampanhaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pipeline-vendas/campanhas")
public class PipelineCampanhaController {
    private final PipelineCampanhaService service;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineCampanhaController(PipelineCampanhaService service, AuthenticatedUserProvider authenticatedUser) {
        this.service = service;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PipelineCampanhaResponseDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(service.listar(authenticatedUser.getUserId())));
    }

    @GetMapping("/leads")
    public ResponseEntity<ApiResponse<List<PipelineCampanhaLeadDTO>>> listarLeads() {
        return ResponseEntity.ok(ApiResponse.ok(service.listarLeads(authenticatedUser.getUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PipelineCampanhaResponseDTO>> criar(
            @Valid @RequestBody PipelineCampanhaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.criar(authenticatedUser.getUserId(), dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PipelineCampanhaResponseDTO>> atualizar(
            @PathVariable Long id, @Valid @RequestBody PipelineCampanhaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizar(id, authenticatedUser.getUserId(), dto)));
    }

    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<ApiResponse<PipelineCampanhaResponseDTO>> alterarAtivacao(
            @PathVariable Long id, @RequestParam boolean ativo) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.alterarAtivacao(id, authenticatedUser.getUserId(), ativo)));
    }
}
