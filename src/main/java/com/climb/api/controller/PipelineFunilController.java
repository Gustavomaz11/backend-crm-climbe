package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineFunilService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pipeline-vendas/funis")
public class PipelineFunilController {
    private final PipelineFunilService service;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineFunilController(PipelineFunilService service, AuthenticatedUserProvider authenticatedUser) {
        this.service = service;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/ativos")
    public ResponseEntity<ApiResponse<List<PipelineFunilResumoResponseDTO>>> listarAtivos() {
        return ResponseEntity.ok(ApiResponse.ok(service.listarAtivos(authenticatedUser.getUserId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PipelineFunilResponseDTO>>> listarTodos() {
        return ResponseEntity.ok(ApiResponse.ok(service.listarTodos(authenticatedUser.getUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PipelineFunilResponseDTO>> criar(
            @Valid @RequestBody PipelineFunilRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.criar(authenticatedUser.getUserId(), dto)));
    }

    @PutMapping("/{funilId}")
    public ResponseEntity<ApiResponse<PipelineFunilResponseDTO>> atualizar(
            @PathVariable Long funilId,
            @Valid @RequestBody PipelineFunilRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizar(funilId, authenticatedUser.getUserId(), dto)));
    }

    @PostMapping("/{funilId}/duplicar")
    public ResponseEntity<ApiResponse<PipelineFunilResponseDTO>> duplicar(@PathVariable Long funilId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.duplicar(funilId, authenticatedUser.getUserId())));
    }

    @PatchMapping("/ordem")
    public ResponseEntity<ApiResponse<List<PipelineFunilResponseDTO>>> reordenar(
            @Valid @RequestBody PipelineOrdemRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.reordenar(authenticatedUser.getUserId(), dto.ids())));
    }
}
