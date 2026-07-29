package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.model.enums.PipelineTarefaVisao;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineComentarioService;
import com.climb.api.service.PipelineHistoricoService;
import com.climb.api.service.PipelineTarefaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pipeline-vendas")
public class PipelineAtividadesController {
    private final PipelineTarefaService tarefaService;
    private final PipelineComentarioService comentarioService;
    private final PipelineHistoricoService historicoService;
    private final AuthenticatedUserProvider authenticatedUser;

    public PipelineAtividadesController(PipelineTarefaService tarefaService,
                                        PipelineComentarioService comentarioService,
                                        PipelineHistoricoService historicoService,
                                        AuthenticatedUserProvider authenticatedUser) {
        this.tarefaService = tarefaService;
        this.comentarioService = comentarioService;
        this.historicoService = historicoService;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping("/tarefas")
    public ResponseEntity<ApiResponse<List<PipelineTarefaResponseDTO>>> listarTarefas(
            @RequestParam(required = false) PipelineTarefaVisao visao,
            @RequestParam(required = false) Long responsavelId,
            @RequestParam(required = false) Long negocioId,
            @RequestParam(required = false) Long funilId,
            @RequestParam(required = false) String tipo) {
        return ResponseEntity.ok(ApiResponse.ok(tarefaService.listar(
                authenticatedUser.getUserId(), visao, responsavelId, negocioId, funilId, tipo
        )));
    }

    @GetMapping("/negocios/{negocioId}/tarefas")
    public ResponseEntity<ApiResponse<List<PipelineTarefaResponseDTO>>> listarTarefasDoNegocio(
            @PathVariable Long negocioId) {
        return ResponseEntity.ok(ApiResponse.ok(
                tarefaService.listarDoNegocio(negocioId, authenticatedUser.getUserId())
        ));
    }

    @PostMapping("/negocios/{negocioId}/tarefas")
    public ResponseEntity<ApiResponse<PipelineTarefaResponseDTO>> criarTarefa(
            @PathVariable Long negocioId,
            @Valid @RequestBody PipelineTarefaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                tarefaService.criar(negocioId, authenticatedUser.getUserId(), dto)
        ));
    }

    @PutMapping("/tarefas/{tarefaId}")
    public ResponseEntity<ApiResponse<PipelineTarefaResponseDTO>> atualizarTarefa(
            @PathVariable Long tarefaId,
            @Valid @RequestBody PipelineTarefaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                tarefaService.atualizar(tarefaId, authenticatedUser.getUserId(), dto)
        ));
    }

    @PatchMapping("/tarefas/{tarefaId}/status")
    public ResponseEntity<ApiResponse<PipelineTarefaResponseDTO>> alterarStatusTarefa(
            @PathVariable Long tarefaId,
            @Valid @RequestBody PipelineTarefaStatusRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                tarefaService.alterarStatus(tarefaId, authenticatedUser.getUserId(), dto.status())
        ));
    }

    @GetMapping("/negocios/{negocioId}/comentarios")
    public ResponseEntity<ApiResponse<List<PipelineComentarioResponseDTO>>> listarComentarios(
            @PathVariable Long negocioId) {
        return ResponseEntity.ok(ApiResponse.ok(
                comentarioService.listar(negocioId, authenticatedUser.getUserId())
        ));
    }

    @PostMapping("/negocios/{negocioId}/comentarios")
    public ResponseEntity<ApiResponse<PipelineComentarioResponseDTO>> criarComentario(
            @PathVariable Long negocioId,
            @Valid @RequestBody PipelineComentarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                comentarioService.criar(negocioId, authenticatedUser.getUserId(), dto)
        ));
    }

    @GetMapping("/negocios/{negocioId}/historico")
    public ResponseEntity<ApiResponse<List<PipelineHistoricoResponseDTO>>> listarHistorico(
            @PathVariable Long negocioId) {
        return ResponseEntity.ok(ApiResponse.ok(
                historicoService.listar(negocioId, authenticatedUser.getUserId())
        ));
    }
}
