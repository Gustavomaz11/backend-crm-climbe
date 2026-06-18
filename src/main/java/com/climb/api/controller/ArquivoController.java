package com.climb.api.controller;

import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.service.CloudflareR2ArquivoStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/arquivos")
@Tag(name = "Arquivos", description = "Upload, validação e persistência de arquivos no Cloudflare R2")
public class ArquivoController {

    private final CloudflareR2ArquivoStorageService storageService;

    public ArquivoController(CloudflareR2ArquivoStorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(
            summary = "Salvar arquivo",
            description = "Lê, valida e salva um arquivo no bucket configurado do Cloudflare R2."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Arquivo salvo com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Arquivo inválido", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Erro ao salvar arquivo", content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ArquivoUploadResponseDTO>> salvar(
            @Parameter(description = "Arquivo enviado pelo usuário") @RequestParam("arquivo") MultipartFile arquivo,
            @Parameter(description = "Prefixo/pasta lógica dentro do bucket") @RequestParam(value = "prefixo", defaultValue = "arquivos") String prefixo) {
        try {
            ArquivoUploadResponseDTO response = storageService.salvar(arquivo, prefixo);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Arquivo salvo com sucesso"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(e.getMessage()));
        }
    }
}
