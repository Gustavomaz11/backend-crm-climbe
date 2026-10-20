package com.climb.api.controller;

import java.util.List;
import jakarta.validation.Valid;
import com.climb.api.model.dto.KanbanArquivamentoRequestDTO;
import com.climb.api.model.dto.KanbanItemArquivadoDTO;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.ContratoKanbanBoardResponseDTO;
import com.climb.api.model.dto.ContratoKanbanAberturaResponseDTO;
import com.climb.api.model.dto.ContratoKanbanMoverTaskRequestDTO;
import com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaConclusaoRequestDTO;
import com.climb.api.model.dto.ContratoKanbanSubtarefaRequestDTO;
import com.climb.api.model.dto.ContratoKanbanTaskRequestDTO;
import com.climb.api.service.ContratoKanbanService;
import com.climb.api.model.enums.ServicoComercial;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contratos/{contratoId}/kanban")
public class ContratoKanbanController {

    private final ContratoKanbanService service;

    public ContratoKanbanController(ContratoKanbanService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> buscarBoard(@PathVariable Long contratoId,
            @RequestParam(required = false) ServicoComercial servico) {
        return ResponseEntity.ok(ApiResponse.ok(service.buscarBoard(contratoId, getAuthenticatedUserId(), servico)));
    }

    @GetMapping("/abertura")
    public ResponseEntity<ApiResponse<ContratoKanbanAberturaResponseDTO>> buscarAbertura(@PathVariable Long contratoId,
            @RequestParam(required = false) ServicoComercial servico) {
        return ResponseEntity.ok(ApiResponse.ok(service.buscarAbertura(contratoId, getAuthenticatedUserId(), servico)));
    }

    @PostMapping("/raias")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> criarRaia(
            @PathVariable Long contratoId,
            @RequestBody ContratoKanbanRaiaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.criarRaia(contratoId, getAuthenticatedUserId(), dto)));
    }

    @PutMapping("/raias/{raiaId}")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> atualizarRaia(
            @PathVariable Long contratoId,
            @PathVariable Long raiaId,
            @RequestBody ContratoKanbanRaiaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizarRaia(contratoId, raiaId, getAuthenticatedUserId(), dto)));
    }

    @DeleteMapping("/raias/{raiaId}")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> removerRaia(
            @PathVariable Long contratoId,
            @PathVariable Long raiaId) {
        return ResponseEntity.ok(ApiResponse.ok(service.removerRaia(contratoId, raiaId, getAuthenticatedUserId())));
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> criarTask(
            @PathVariable Long contratoId,
            @RequestBody ContratoKanbanTaskRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.criarTask(contratoId, getAuthenticatedUserId(), dto)));
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> atualizarTask(
            @PathVariable Long contratoId,
            @PathVariable Long taskId,
            @RequestBody ContratoKanbanTaskRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.atualizarTask(contratoId, taskId, getAuthenticatedUserId(), dto)));
    }

    @PatchMapping("/tasks/{taskId}/raia")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> moverTask(
            @PathVariable Long contratoId,
            @PathVariable Long taskId,
            @RequestBody ContratoKanbanMoverTaskRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.moverTask(contratoId, taskId, getAuthenticatedUserId(), dto)));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> removerTask(
            @PathVariable Long contratoId,
            @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(service.removerTask(contratoId, taskId, getAuthenticatedUserId())));
    }

    @PostMapping("/tasks/{taskId}/subtasks")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> criarSubtarefa(
            @PathVariable Long contratoId,
            @PathVariable Long taskId,
            @RequestBody ContratoKanbanSubtarefaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.criarSubtarefa(contratoId, taskId, getAuthenticatedUserId(), dto)
        ));
    }

    @PutMapping("/tasks/{taskId}/subtasks/{subtarefaId}")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> atualizarSubtarefa(
            @PathVariable Long contratoId,
            @PathVariable Long taskId,
            @PathVariable Long subtarefaId,
            @RequestBody ContratoKanbanSubtarefaRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.atualizarSubtarefa(contratoId, taskId, subtarefaId, getAuthenticatedUserId(), dto)
        ));
    }

    @PatchMapping("/tasks/{taskId}/subtasks/{subtarefaId}/conclusao")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> atualizarConclusaoSubtarefa(
            @PathVariable Long contratoId,
            @PathVariable Long taskId,
            @PathVariable Long subtarefaId,
            @RequestBody ContratoKanbanSubtarefaConclusaoRequestDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.atualizarConclusaoSubtarefa(
                        contratoId,
                        taskId,
                        subtarefaId,
                        getAuthenticatedUserId(),
                        dto
                )
        ));
    }

    @DeleteMapping("/tasks/{taskId}/subtasks/{subtarefaId}")
    public ResponseEntity<ApiResponse<ContratoKanbanBoardResponseDTO>> removerSubtarefa(
            @PathVariable Long contratoId,
            @PathVariable Long taskId,
            @PathVariable Long subtarefaId) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.removerSubtarefa(contratoId, taskId, subtarefaId, getAuthenticatedUserId())
        ));
    }

    @GetMapping("/arquivados")
    public List<KanbanItemArquivadoDTO> listarArquivados(@PathVariable Long contratoId,
            @RequestParam(required = false) ServicoComercial servico) {
        return service.listarArquivados(contratoId, getAuthenticatedUserId(), servico);
    }

    @PatchMapping("/tasks/{taskId}/arquivamento")
    public void alterarArquivamento(@PathVariable Long contratoId, @PathVariable Long taskId,
            @Valid @RequestBody KanbanArquivamentoRequestDTO dto) {
        service.alterarArquivamento(contratoId, taskId, getAuthenticatedUserId(), dto.arquivado());
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }

        Object details = auth.getDetails();
        if (details instanceof Long userId) {
            return userId;
        }
        if (details instanceof Integer userId) {
            return userId.longValue();
        }
        if (details instanceof String userId) {
            return Long.parseLong(userId);
        }

        throw new IllegalStateException("Usuário não autenticado");
    }
}
