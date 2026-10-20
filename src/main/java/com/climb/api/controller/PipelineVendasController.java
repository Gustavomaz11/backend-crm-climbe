package com.climb.api.controller;

import java.util.List;
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

    @GetMapping("/arquivados")
    public List<KanbanItemArquivadoDTO> listarArquivados(@RequestParam(required = false) Long funilId) {
        return service.listarArquivados(authenticatedUser.getUserId(), funilId);
    }

    @PatchMapping("/negocios/{negocioId}/arquivamento")
    public void alterarArquivamento(@PathVariable Long negocioId,
            @Valid @RequestBody KanbanArquivamentoRequestDTO dto) {
        service.alterarArquivamento(negocioId, authenticatedUser.getUserId(), dto.arquivado());
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
                negocioId, authenticatedUser.getUserId(), dto.etapaId(), dto.motivoPerdaId(), dto.observacaoPerda(),
                dto.responsavelTecnicoId(), dto.propostaId(), dto.responsaveisPorServico())));
    }

    @PatchMapping("/negocios/{negocioId}/ganhar")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> ganhar(@PathVariable Long negocioId,
            @RequestBody(required = false) PipelineGanhoRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.marcarResultado(
                        negocioId, authenticatedUser.getUserId(), PipelineVendasResultado.GANHO, null, null,
                        dto == null ? null : dto.responsavelTecnicoId(), dto == null ? null : dto.propostaId(),
                        dto == null ? null : dto.responsaveisPorServico())
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

    public record ConversaoPreVendaInput(Long funilId, Long responsavelId) {}
    @PostMapping("/negocios/{negocioId}/converter-venda")
    public ResponseEntity<ApiResponse<PipelineNegocioResponseDTO>> converterVenda(@PathVariable Long negocioId, @RequestBody ConversaoPreVendaInput dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.ganharPreVenda(negocioId, authenticatedUser.getUserId(), dto.funilId(), dto.responsavelId())));
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
    @GetMapping("/negocios/{id}")
    public ApiResponse<PipelineNegocioResponseDTO> buscar(@PathVariable Long id) { return ApiResponse.ok(service.buscar(id, authenticatedUser.getUserId())); }
    @PostMapping("/negocios/lote")
    public ApiResponse<List<PipelineNegocioResponseDTO>> lote(@Valid @RequestBody List<@Valid PipelineNegocioRequestDTO> dados) { return ApiResponse.ok(service.criarLote(authenticatedUser.getUserId(), dados)); }
    @PostMapping("/negocios/{id}/reiniciar-cadencia")
    public ApiResponse<String> reiniciar(@PathVariable Long id) { service.reiniciarCadencia(id, authenticatedUser.getUserId()); return ApiResponse.ok("Cadência reiniciada"); }

}
