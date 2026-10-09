package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.TarefaColaboracaoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/tarefas/{tipo}/{tarefaId}/colaboracao")
public class TarefaColaboracaoController {
    private final TarefaColaboracaoService service;
    private final AuthenticatedUserProvider auth;
    public TarefaColaboracaoController(TarefaColaboracaoService service, AuthenticatedUserProvider auth) {
        this.service = service; this.auth = auth;
    }
    @GetMapping
    public ApiResponse<TarefaColaboracaoResponseDTO> listar(@PathVariable TarefaTipo tipo, @PathVariable Long tarefaId) {
        return ApiResponse.ok(service.listar(tipo, tarefaId, auth.getUserId()));
    }
    @PostMapping(value = "/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<TarefaAnexoResponseDTO>> anexar(@PathVariable TarefaTipo tipo, @PathVariable Long tarefaId,
            @RequestPart("arquivos") List<MultipartFile> arquivos, @RequestParam(required = false) Long pastaId) {
        return ApiResponse.ok(service.anexar(tipo, tarefaId, auth.getUserId(), arquivos, pastaId));
    }
    @PostMapping("/pastas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TarefaPastaResponseDTO> criarPasta(@PathVariable TarefaTipo tipo, @PathVariable Long tarefaId,
            @RequestBody TarefaPastaRequestDTO dto) {
        return ApiResponse.ok(service.criarPasta(tipo, tarefaId, auth.getUserId(), dto));
    }
    @PostMapping(value = "/comentarios", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TarefaComentarioResponseDTO> comentar(@PathVariable TarefaTipo tipo, @PathVariable Long tarefaId,
            @RequestParam String conteudo, @RequestParam(required = false) Long comentarioPaiId,
            @RequestPart(value = "arquivos", required = false) List<MultipartFile> arquivos) {
        return ApiResponse.ok(service.comentar(tipo, tarefaId, auth.getUserId(), conteudo, comentarioPaiId, arquivos));
    }
    @GetMapping("/anexos/{anexoId}/conteudo")
    public ResponseEntity<byte[]> baixar(@PathVariable TarefaTipo tipo, @PathVariable Long tarefaId,
            @PathVariable Long anexoId, @RequestParam(defaultValue = "false") boolean download) {
        var conteudo = service.baixar(tipo, tarefaId, anexoId, auth.getUserId());
        var disposition = download ? ContentDisposition.attachment() : ContentDisposition.inline();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(conteudo.anexo().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(conteudo.anexo().nome(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff").body(conteudo.conteudo());
    }
}
