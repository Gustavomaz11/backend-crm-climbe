package com.climb.api.controller;

import com.climb.api.model.Contrato;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.HistoricoAprovacaoContratoResponseDTO;
import com.climb.api.service.ContratoService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contratos")
public class ContratoController {

    private final ContratoService service;

    public ContratoController(ContratoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Contrato> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Contrato buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @GetMapping("/status/{status}")
    public List<Contrato> listarPorStatus(@PathVariable String status) {
        return service.listarPorStatus(status);
    }

    @PostMapping
    public Contrato criar(@RequestBody Contrato contrato) {
        return service.criar(contrato);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Contrato criarComUpload(
            @RequestParam(value = "empresaId", required = false) Long empresaId,
            @RequestParam(value = "propostaId", required = false) Long propostaId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        return service.criarComArquivo(empresaId, propostaId, getAuthenticatedUserId(), arquivo);
    }

    @PutMapping("/{id}")
    public Contrato atualizar(@PathVariable Long id, @RequestBody Contrato atualizado) {
        return service.atualizar(id, atualizado);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        service.deletar(id);
    }

    @PatchMapping(value = "/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Contrato enviarPdf(
            @PathVariable Long id,
            @Parameter(description = "Arquivo PDF do contrato") @RequestParam("arquivo") MultipartFile arquivo) {
        return service.enviarPdf(id, arquivo);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> baixarPdf(@PathVariable Long id) {
        return service.baixarPdf(id);
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<ApiResponse<String>> gerarUrlDownload(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.gerarUrlDownload(id)));
    }

    @PatchMapping("/{id}/aprovar")
    public Contrato aprovar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.aprovar(id, getAuthenticatedUserId(), body.get("status"));
    }

    @GetMapping("/{id}/historico")
    public ResponseEntity<ApiResponse<List<HistoricoAprovacaoContratoResponseDTO>>> historico(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.listarHistorico(id)));
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
