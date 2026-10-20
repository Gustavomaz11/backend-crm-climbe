package com.climb.api.controller;

import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.DriveTarefaService;
import com.climb.api.service.GoogleDriveClient;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/tarefas/CONTRATO/{tarefaId}/drive")
public class DriveTarefaController {
    private final DriveTarefaService service;
    private final AuthenticatedUserProvider auth;
    public DriveTarefaController(DriveTarefaService service, AuthenticatedUserProvider auth) { this.service = service; this.auth = auth; }

    @GetMapping
    public ApiResponse<DriveTarefaService.Pastas> servicos(@PathVariable Long tarefaId) {
        return ApiResponse.ok(service.servicos(tarefaId, auth.getUserId()));
    }

    @GetMapping("/{servico}/arquivos")
    public ApiResponse<List<GoogleDriveClient.Arquivo>> listar(@PathVariable Long tarefaId, @PathVariable ServicoComercial servico,
            @RequestParam(required = false) String pastaId) {
        return ApiResponse.ok(service.listar(tarefaId, auth.getUserId(), servico, pastaId));
    }

    @PostMapping("/{servico}/pastas") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GoogleDriveClient.Arquivo> criarPasta(@PathVariable Long tarefaId, @PathVariable ServicoComercial servico,
            @RequestBody DriveTarefaService.NovaPasta dto) {
        return ApiResponse.ok(service.criarPasta(tarefaId, auth.getUserId(), servico, dto));
    }

    @PostMapping(value = "/{servico}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<GoogleDriveClient.Arquivo>> enviar(@PathVariable Long tarefaId, @PathVariable ServicoComercial servico,
            @RequestParam(required = false) String pastaId, @RequestPart("arquivos") List<MultipartFile> arquivos) {
        return ApiResponse.ok(service.enviar(tarefaId, auth.getUserId(), servico, pastaId, arquivos));
    }

    @GetMapping("/{servico}/arquivos/{arquivoId}/conteudo")
    public ResponseEntity<byte[]> baixar(@PathVariable Long tarefaId, @PathVariable ServicoComercial servico,
            @PathVariable String arquivoId, @RequestParam(defaultValue = "false") boolean download) {
        var arquivo = service.baixar(tarefaId, auth.getUserId(), servico, arquivoId);
        // Arquivos HTML e SVG são baixados para não executar conteúdo na origem autenticada do sistema.
        boolean ativo = arquivo.contentType().contains("html") || arquivo.contentType().contains("svg") || arquivo.contentType().contains("javascript");
        var disposition = download || ativo ? ContentDisposition.attachment() : ContentDisposition.inline();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(arquivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff").body(arquivo.bytes());
    }
}
