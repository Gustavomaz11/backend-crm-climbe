package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineVendasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pipeline-vendas")
public class PipelineVendasController {
    private final PipelineVendasService service;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineVendasController(PipelineVendasService service, AuthenticatedUserProvider authenticatedUser) {
        this.service = service;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PipelineBoardResponseDTO>> buscarBoard(
            @RequestParam(required = false) Long funilId) {
        return ResponseEntity.ok(ApiResponse.ok(service.buscarBoard(authenticatedUser.getUserId(), funilId)));
    }

    @PostMapping("/negocios")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> criar(
            @Valid @RequestBody PipelineNegocioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.criar(authenticatedUser.getUserId(), dto)));
    }

    @PutMapping("/negocios/{negocioId}")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> atualizar(
            @PathVariable Long negocioId,
            @Valid @RequestBody PipelineNegocioRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizar(negocioId, authenticatedUser.getUserId(), dto)));
    }

    @PatchMapping("/negocios/{negocioId}/etapa")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> mover(
            @PathVariable Long negocioId,
            @Valid @RequestBody PipelineMoverNegocioRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.mover(
                negocioId, authenticatedUser.getUserId(), dto.etapaId(), dto.motivoPerdaId(), dto.observacaoPerda())));
    }

    @PatchMapping("/negocios/{negocioId}/ganhar")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> ganhar(@PathVariable Long negocioId) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.marcarResultado(
                        negocioId, authenticatedUser.getUserId(), PipelineVendasResultado.GANHO, null, null)
        ));
    }

    @PatchMapping("/negocios/{negocioId}/perder")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> perder(
            @PathVariable Long negocioId,
            @Valid @RequestBody PipelinePerdaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.marcarResultado(negocioId, authenticatedUser.getUserId(), PipelineVendasResultado.PERDIDO,
                        dto.motivoPerdaId(), dto.observacaoPerda())
        ));
    }

    @PatchMapping("/negocios/{negocioId}/reativar")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> reativar(
            @PathVariable Long negocioId,
            @RequestBody(required = false) PipelineReativarNegocioRequestDTO dto) {
        Long etapaId = dto == null ? null : dto.etapaId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.reativar(negocioId, authenticatedUser.getUserId(), etapaId),
                "Negócio reativado"
        ));
    }

    @PostMapping("/negocios/{negocioId}/converter-contrato")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> converterContrato(
            @PathVariable Long negocioId,
            @RequestBody(required = false) PipelineConverterContratoRequestDTO dto) {
        Long empresaId = dto == null ? null : dto.empresaId();
        return ResponseEntity.ok(ApiResponse.ok(
                service.converterEmContrato(negocioId, authenticatedUser.getUserId(), empresaId),
                "Negócio convertido em contrato"
        ));
    }
}
