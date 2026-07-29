package com.climb.api.controller;

import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.RevisaoClienteRequestDTO;
import com.climb.api.model.dto.RevisaoDocumentoResponseDTO;
import com.climb.api.model.dto.RevisaoReprovacaoRequestDTO;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import com.climb.api.service.RevisaoDocumentoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/revisoes")
public class RevisaoDocumentoController {
    private final RevisaoDocumentoService service;

    public RevisaoDocumentoController(RevisaoDocumentoService service) {
        this.service = service;
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> buscarPublica(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(service.buscarPublica(token)));
    }

    @GetMapping(value = "/public/{token}/paginas/{pagina}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> paginaPublica(@PathVariable String token, @PathVariable int pagina) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .contentType(MediaType.IMAGE_PNG)
                .body(service.paginaPublica(token, pagina));
    }

    @PostMapping("/public/{token}/enviar-revisao")
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> enviarRevisao(
            @PathVariable String token,
            @Valid @RequestBody RevisaoClienteRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok(service.enviarParaRevisao(token, request)));
    }

    @PostMapping("/public/{token}/aprovar")
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> aprovar(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(service.aprovar(token)));
    }

    @PostMapping("/public/{token}/reprovar")
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> reprovar(
            @PathVariable String token,
            @Valid @RequestBody RevisaoReprovacaoRequestDTO request) {
        return ResponseEntity.ok(ApiResponse.ok(service.reprovar(token, request)));
    }

    @GetMapping("/{tipo}/{referenciaId}")
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> buscarInterna(
            @PathVariable RevisaoDocumentoTipo tipo,
            @PathVariable Long referenciaId) {
        return ResponseEntity.ok(ApiResponse.ok(service.buscarInterna(tipo, referenciaId)));
    }

    @GetMapping(value = "/{id}/paginas/{versao}/{pagina}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> paginaInterna(@PathVariable Long id,
                                                @PathVariable int versao,
                                                @PathVariable int pagina) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .contentType(MediaType.IMAGE_PNG)
                .body(service.paginaInterna(id, versao, pagina));
    }

    @PostMapping(value = "/{id}/nova-versao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> novaVersao(
            @PathVariable Long id,
            @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(ApiResponse.ok(service.novaVersao(id, getAuthenticatedUserId(), arquivo)));
    }

    @PostMapping("/{id}/reenviar")
    public ResponseEntity<ApiResponse<RevisaoDocumentoResponseDTO>> reenviar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.reenviar(id, getAuthenticatedUserId())));
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        Object details = auth.getDetails();
        if (details instanceof Number id) return id.longValue();
        if (details instanceof String id) return Long.parseLong(id);
        throw new IllegalStateException("Usuário não autenticado");
    }
}
