package com.climb.api.controller;

import com.climb.api.model.dto.DocumentoResponseDTO;
import com.climb.api.model.dto.DocumentoSolicitacaoRequestDTO;
import com.climb.api.model.dto.DocumentoUploadInfoResponseDTO;
import com.climb.api.model.dto.DocumentoValidacaoRequestDTO;
import com.climb.api.service.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/documentos")
@Tag(name = "Documentos", description = "Endpoints internos de gestão documental ")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @Operation(summary = "Listar todos os documentos")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping
    public ResponseEntity<List<DocumentoResponseDTO>> listar() {
        List<DocumentoResponseDTO> lista = documentoService.listar();
        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Listar documentos por empresa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Empresa não encontrada")
    })
    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<DocumentoResponseDTO>> listarPorEmpresa(@PathVariable Long empresaId) {
        List<DocumentoResponseDTO> lista = documentoService.listarPorEmpresa(empresaId);
        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Buscar documento por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento encontrado"),
            @ApiResponse(responseCode = "404", description = "Documento não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentoResponseDTO> buscarPorId(@PathVariable Long id) {
        DocumentoResponseDTO documento = documentoService.buscarPorId(id);
        return ResponseEntity.ok(documento);
    }

    @Operation(summary = "Buscar dados públicos de solicitação por token")
    @GetMapping("/public/{token}")
    public ResponseEntity<DocumentoUploadInfoResponseDTO> buscarPorToken(@PathVariable String token) {
        return ResponseEntity.ok(documentoService.buscarSolicitacaoPorToken(token));
    }

    @Operation(
            summary = "Solicitar documento",
            description = "O usuário interno solicita um documento para a empresa. O status inicial é definido automaticamente como PENDENTE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitação criada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Empresa ou analista não encontrado")
    })
    @PostMapping("/solicitar")
    public ResponseEntity<DocumentoResponseDTO> solicitar(@Valid @RequestBody DocumentoSolicitacaoRequestDTO dto) {
        DocumentoResponseDTO criado = documentoService.solicitar(dto, getAuthenticatedUserId());
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/documentos/{id}")
                .buildAndExpand(criado.id())
                .toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(
            summary = "Validar documento",
            description = "O analista aprova ou rejeita o documento pelo ID da solicitação. Valores aceitos: APROVADO ou REPROVADO."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento validado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Documento não encontrado")
    })
    @PatchMapping("/{id}/validar")
    public ResponseEntity<DocumentoResponseDTO> validar(
            @PathVariable Long id,
            @Valid @RequestBody DocumentoValidacaoRequestDTO dto) {
        DocumentoResponseDTO atualizado = documentoService.validar(id, dto);
        return ResponseEntity.ok(atualizado);
    }

    @Operation(summary = "Gerar URL temporária de download do documento")
    @GetMapping("/{id}/download-url")
    public ResponseEntity<String> gerarUrlDownload(@PathVariable Long id) {
        return ResponseEntity.ok(documentoService.gerarUrlDownload(id));
    }

    @Operation(summary = "Deletar documento")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Documento deletado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Documento não encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        documentoService.deletar(id);
        return ResponseEntity.noContent().build();
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
