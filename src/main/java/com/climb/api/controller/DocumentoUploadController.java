package com.climb.api.controller;

import com.climb.api.model.dto.DocumentoResponseDTO;
import com.climb.api.service.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documentos")
@Tag(name = "Upload de Documentos", description = "Endpoint autenticados para envio de documentos")
public class DocumentoUploadController {


    private final DocumentoService documentoService;

    public DocumentoUploadController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @Operation(
            summary = "Enviar arquivo do documento",
            description = "A empresa envia o arquivo através do link recebido. O status é atualizado automaticamente para EM_ANALISE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo enviado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Documento não encontrado", content = @Content),
            @ApiResponse(responseCode = "415", description = "Tipo de mídia não suportado", content = @Content)
    })
    @PatchMapping(value = "/{id}/enviar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoResponseDTO> enviar(
            @Parameter(description = "ID da solicitação gerada pelo analista") @PathVariable Long id,
            @Parameter(description = "Arquivo PDF do documento") @RequestParam("arquivo") MultipartFile arquivo) {
        DocumentoResponseDTO atualizado = documentoService.enviar(id, arquivo);
        return ResponseEntity.ok(atualizado);
    }

    @Operation(
            summary = "Enviar arquivo do documento por link público",
            description = "Recebe o arquivo usando o token enviado por e-mail e atualiza a solicitação para EM_ANALISE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo enviado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Solicitação não encontrada", content = @Content),
            @ApiResponse(responseCode = "415", description = "Tipo de mídia não suportado", content = @Content)
    })
    @PatchMapping(value = "/public/{token}/enviar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoResponseDTO> enviarPorToken(
            @Parameter(description = "Token enviado por e-mail") @PathVariable String token,
            @Parameter(description = "Arquivo do documento") @RequestParam("arquivo") MultipartFile arquivo) {
        DocumentoResponseDTO atualizado = documentoService.enviarPorToken(token, arquivo);
        return ResponseEntity.ok(atualizado);
    }
}
