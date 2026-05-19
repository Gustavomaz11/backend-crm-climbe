package com.climb.api.controller;

import com.climb.api.model.dto.RelatorioPdfDownloadDTO;
import com.climb.api.model.dto.RelatorioRequestDTO;
import com.climb.api.model.dto.RelatorioResponseDTO;
import com.climb.api.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/relatorios")
@Tag(name = "Relatórios", description = "Endpoints internos de gestão de relatórios")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar relatório com PDF",
            description = "Cria um relatório a partir dos dados informados e anexa um arquivo PDF enviado via multipart/form-data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatório criado e PDF anexado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Contrato obrigatório, arquivo ausente, inválido ou corrompido"),
            @ApiResponse(responseCode = "404", description = "Contrato não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro ao salvar o PDF do relatório")
    })
    @PostMapping(value = "/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RelatorioResponseDTO> uploadPdf(
            @RequestParam("contratoId") Long contratoId,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(service.uploadPdf(contratoId, descricao, file));
    }

    @Operation(summary = "Listar todos os relatórios")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping
    public ResponseEntity<List<RelatorioResponseDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @Operation(summary = "Listar relatórios por contrato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    @GetMapping("/contrato/{contratoId}")
    public ResponseEntity<List<RelatorioResponseDTO>> listarPorContrato(
            @PathVariable Long contratoId
    ) {
        return ResponseEntity.ok(service.listarPorContrato(contratoId));
    }

    @Operation(summary = "Buscar relatório por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatório encontrado"),
            @ApiResponse(responseCode = "404", description = "Relatório não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RelatorioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorIdResponse(id));
    }

    @Operation(
            summary = "Atualizar relatório",
            description = "Atualiza os dados de um relatório. O documento PDF vinculado deve ser alterado pelo endpoint de upload."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatório atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Relatório ou contrato não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<RelatorioResponseDTO> atualizar(
            @PathVariable Long id,
            @RequestBody RelatorioRequestDTO dto
    ) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Visualizar PDF anexado ao relatório",
            description = "Retorna o PDF previamente anexado ao relatório para visualização inline no navegador. Este endpoint não gera PDF automaticamente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF anexado retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Relatório ou PDF anexado não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro ao carregar o PDF anexado")
    })
    @GetMapping("/{id}/visualizar-pdf")
    public ResponseEntity<byte[]> visualizarPdf(@PathVariable Long id) {
        RelatorioPdfDownloadDTO pdf = service.obterPdfInline(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.nomeArquivo()).build().toString())
                .body(pdf.conteudo());
    }

    @Operation(
            summary = "Baixar PDF anexado ao relatório",
            description = "Retorna o PDF previamente anexado ao relatório como anexo para download. Este endpoint não gera PDF automaticamente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF anexado baixado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Relatório ou PDF anexado não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro ao carregar o PDF anexado")
    })
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> baixarPdf(@PathVariable Long id) {
        RelatorioPdfDownloadDTO pdf = service.obterPdfParaDownload(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.nomeArquivo()).build().toString())
                .body(pdf.conteudo());
    }

    @Operation(summary = "Deletar relatório")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Relatório deletado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Relatório não encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }
}