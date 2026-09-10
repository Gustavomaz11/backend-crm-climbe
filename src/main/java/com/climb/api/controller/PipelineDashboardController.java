package com.climb.api.controller;

import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.PipelineDashboardFiltroDTO;
import com.climb.api.model.dto.PipelineDashboardResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/pipeline-vendas/dashboard")
public class PipelineDashboardController {
    private final PipelineDashboardService service;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineDashboardController(PipelineDashboardService service,
                                       AuthenticatedUserProvider authenticatedUser) {
        this.service = service;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PipelineDashboardResponseDTO>> buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Long responsavelId,
            @RequestParam(required = false) Long funilId,
            @RequestParam(required = false) String estrategia,
            @RequestParam(required = false) String servico,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) PipelineVendasResultado situacao,
            @RequestParam(required = false) Long campanhaId) {
        PipelineDashboardFiltroDTO filtro = new PipelineDashboardFiltroDTO(
                dataInicio, dataFim, responsavelId, funilId, estrategia, servico, origem, empresaId, situacao, campanhaId);
        return ResponseEntity.ok(ApiResponse.ok(service.buscar(authenticatedUser.getUserId(), filtro)));
    }
}
