package com.climb.api.controller;

import com.climb.api.model.dto.EmpresaComprovanteDTO;
import com.climb.api.service.EmpresaComprovanteService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/empresas/{empresaId}/comprovantes")
public class EmpresaComprovanteController {
    private final EmpresaComprovanteService service;
    public EmpresaComprovanteController(EmpresaComprovanteService service) { this.service = service; }

    @GetMapping
    public List<EmpresaComprovanteDTO> listar(@PathVariable Long empresaId) { return service.listar(empresaId); }

    @PostMapping("/{tipo}/solicitar")
    public List<EmpresaComprovanteDTO> solicitar(@PathVariable Long empresaId, @PathVariable String tipo) { return service.solicitar(empresaId, tipo); }

    @PostMapping("/{id}/tentar-novamente")
    public ResponseEntity<Void> tentarNovamente(@PathVariable Long empresaId, @PathVariable Long id) {
        service.tentarNovamente(empresaId, id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> visualizar(@PathVariable Long empresaId, @PathVariable Long id) {
        var documento = service.buscar(empresaId, id);
        if (!"DISPONIVEL".equals(documento.getStatus()) || documento.getConteudo() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O comprovante ainda não está disponível.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("comprovante-" + documento.getTipo().toLowerCase() + ".pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff").body(documento.getConteudo());
    }
}
