package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineScriptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pipeline-vendas/scripts")
public class PipelineScriptController {
    private final PipelineScriptService service;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineScriptController(PipelineScriptService service, AuthenticatedUserProvider authenticatedUser) {
        this.service = service;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PipelineScriptResponseDTO>>> listar(
            @RequestParam(defaultValue = "false") boolean somenteAtivos) {
        return ResponseEntity.ok(ApiResponse.ok(service.listar(authenticatedUser.getUserId(), somenteAtivos)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PipelineScriptResponseDTO>> criar(
            @Valid @RequestBody PipelineScriptRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.criar(authenticatedUser.getUserId(), dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PipelineScriptResponseDTO>> atualizar(
            @PathVariable Long id, @Valid @RequestBody PipelineScriptRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizar(id, authenticatedUser.getUserId(), dto)));
    }

    @GetMapping("/{id}/desempenho")
    public ResponseEntity<ApiResponse<PipelineScriptDesempenhoDTO>> desempenho(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.desempenho(id, authenticatedUser.getUserId())));
    }
}
