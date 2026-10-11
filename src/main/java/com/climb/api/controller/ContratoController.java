package com.climb.api.controller;

import jakarta.validation.Valid;
import com.climb.api.model.dto.KanbanArquivamentoRequestDTO;
import com.climb.api.model.dto.KanbanItemArquivadoDTO;
import com.climb.api.model.Contrato;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.ContratoResponsaveisRequestDTO;
import com.climb.api.model.dto.ContratoPreparacaoRequestDTO;
import com.climb.api.model.dto.RevisaoDocumentoResponseDTO;
import com.climb.api.model.dto.ContratoParcelaVencimentoRequestDTO;
import com.climb.api.model.dto.HistoricoAprovacaoContratoResponseDTO;
import com.climb.api.service.ContratoService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@permissaoAcesso.contrato('LER')")
    public List<Contrato> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissaoAcesso.lerContrato(#id)")
    public Contrato buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("@permissaoAcesso.contrato('LER')")
    public List<Contrato> listarPorStatus(@PathVariable String status) {
        return service.listarPorStatus(status);
    }

    @PostMapping
    @PreAuthorize("@permissaoAcesso.contrato('CRIAR')")
    public Contrato criar(@RequestBody Contrato contrato) {
        return service.criar(contrato, getAuthenticatedUserId());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissaoAcesso.criarContratoComUpload()")
    public Contrato criarComUpload(
            @RequestParam(value = "empresaId", required = false) Long empresaId,
            @RequestParam(value = "propostaId", required = false) Long propostaId,
            @RequestParam(value = "responsavelId", required = false) Long responsavelId,
            @RequestParam(value = "participanteIds", required = false) List<Long> participanteIds,
            @RequestParam("arquivo") MultipartFile arquivo) {
        return service.criarComArquivo(empresaId, propostaId, getAuthenticatedUserId(), responsavelId, participanteIds, arquivo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissaoAcesso.contrato('EDITAR')")
    public Contrato atualizar(@PathVariable Long id, @RequestBody Contrato atualizado) {
        return service.atualizar(id, atualizado, getAuthenticatedUserId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissaoAcesso.contrato('DELETAR')")
    public void deletar(@PathVariable Long id) {
        service.deletar(id);
    }

    @PatchMapping(value = "/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissaoAcesso.contrato('ANEXAR_ARQUIVO')")
    public Contrato enviarPdf(
            @PathVariable Long id,
            @Parameter(description = "Arquivo PDF do contrato") @RequestParam("arquivo") MultipartFile arquivo) {
        return service.enviarPdf(id, arquivo);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("@permissaoAcesso.lerContrato(#id)")
    public ResponseEntity<Resource> baixarPdf(@PathVariable Long id) {
        return service.baixarPdf(id);
    }

    @PatchMapping("/{id}/etapa-preparacao")
    @PreAuthorize("@permissaoAcesso.contrato('MOVIMENTAR')")
    public Contrato moverPreparacao(@PathVariable Long id,
            @Valid @RequestBody ContratoPreparacaoRequestDTO dto) {
        return service.moverPreparacao(id, getAuthenticatedUserId(), dto.etapa());
    }

    @PostMapping(value = "/{id}/enviar-cliente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissaoAcesso.contrato('ENVIAR_CLIENTE')")
    public ApiResponse<RevisaoDocumentoResponseDTO> enviarAoCliente(@PathVariable Long id,
            @RequestParam("arquivo") MultipartFile arquivo) {
        return ApiResponse.ok(service.enviarAoCliente(id, getAuthenticatedUserId(), arquivo));
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("@permissaoAcesso.lerContrato(#id)")
    public ResponseEntity<ApiResponse<String>> gerarUrlDownload(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.gerarUrlDownload(id)));
    }

    @PatchMapping("/{id}/aprovar")
    @PreAuthorize("@permissaoAcesso.contrato('APROVAR')")
    public Contrato aprovar(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.aprovar(id, getAuthenticatedUserId(), body.get("status"));
    }

    @PatchMapping("/{id}/desvincular-proposta")
    @PreAuthorize("@permissaoAcesso.contrato('DESVINCULAR_PROPOSTA')")
    public Contrato desvincularProposta(@PathVariable Long id) {
        return service.desvincularProposta(id, getAuthenticatedUserId());
    }

    @PatchMapping("/{id}/responsaveis")
    @PreAuthorize("@permissaoAcesso.contrato('RESPONSAVEIS_EDITAR')")
    public Contrato atualizarResponsaveis(@PathVariable Long id, @RequestBody ContratoResponsaveisRequestDTO dto) {
        return service.atualizarResponsaveis(id, getAuthenticatedUserId(), dto.responsavelId(), dto.participanteIds());
    }

    @PatchMapping("/{id}/parcelas/{parcelaId}/vencimento")
    @PreAuthorize("@permissaoAcesso.contrato('VENCIMENTOS_EDITAR')")
    public Contrato alterarVencimentoParcela(
            @PathVariable Long id,
            @PathVariable Long parcelaId,
            @RequestBody ContratoParcelaVencimentoRequestDTO dto) {
        return service.alterarVencimentoParcela(id, parcelaId, getAuthenticatedUserId(), dto.vencimento());
    }

    @GetMapping("/{id}/historico")
    @PreAuthorize("@permissaoAcesso.lerContrato(#id)")
    public ResponseEntity<ApiResponse<List<HistoricoAprovacaoContratoResponseDTO>>> historico(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.listarHistorico(id)));
    }

    @GetMapping("/arquivados")
    @PreAuthorize("@permissaoAcesso.contrato('LER')")
    public List<KanbanItemArquivadoDTO> listarArquivados() {
        return service.listarArquivados(getAuthenticatedUserId());
    }

    @PatchMapping("/{id}/arquivamento")
    @PreAuthorize("@permissaoAcesso.contrato(#dto.arquivado() ? 'ARQUIVAR' : 'DESARQUIVAR')")
    public void alterarArquivamento(@PathVariable Long id,
            @Valid @RequestBody KanbanArquivamentoRequestDTO dto) {
        service.alterarArquivamento(id, getAuthenticatedUserId(), dto.arquivado());
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
