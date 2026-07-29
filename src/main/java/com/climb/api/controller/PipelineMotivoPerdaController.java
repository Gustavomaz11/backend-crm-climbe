package com.climb.api.controller;

import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.PipelineMotivoPerdaRequestDTO;
import com.climb.api.model.dto.PipelineMotivoPerdaResponseDTO;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineMotivoPerdaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pipeline-vendas/motivos-perda")
public class PipelineMotivoPerdaController {
    private final PipelineMotivoPerdaService service;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineMotivoPerdaController(PipelineMotivoPerdaService service,
                                         AuthenticatedUserProvider authenticatedUser) {
        this.service = service;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/ativos")
    public ResponseEntity<ApiResponse<List<PipelineMotivoPerdaResponseDTO>>> listarAtivos() {
        return ResponseEntity.ok(ApiResponse.ok(service.listarAtivos(authenticatedUser.getUserId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PipelineMotivoPerdaResponseDTO>>> listarTodos() {
        return ResponseEntity.ok(ApiResponse.ok(service.listarTodos(authenticatedUser.getUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PipelineMotivoPerdaResponseDTO>> criar(
            @Valid @RequestBody PipelineMotivoPerdaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.criar(authenticatedUser.getUserId(), dto)));
    }

    @PutMapping("/{motivoId}")
    public ResponseEntity<ApiResponse<PipelineMotivoPerdaResponseDTO>> atualizar(
            @PathVariable Long motivoId,
            @Valid @RequestBody PipelineMotivoPerdaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizar(motivoId, authenticatedUser.getUserId(), dto)));
    }
}
