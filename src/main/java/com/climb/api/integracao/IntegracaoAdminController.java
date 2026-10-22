package com.climb.api.integracao;

import com.climb.api.repository.EmpresaRepository;
import com.climb.api.security.AuthenticatedUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/integracoes") @RequiredArgsConstructor
@PreAuthorize("@permissaoAcesso.tem('INTEGRACOES_GERENCIAR')")
public class IntegracaoAdminController {
    private final IntegracaoService service;
    private final IntegracaoClienteRepository repository;
    private final IntegracaoCatalogo catalogo;
    private final IntegracaoPdfService pdf;
    private final IntegracaoAuditoriaRepository auditoria;
    private final AuthenticatedUserProvider usuario;
    private final EmpresaRepository empresas;
    public record EmpresaOpcao(Long id, String nome, String cnpj) {}

    @GetMapping
    public List<IntegracaoDTO.Resumo> listar() { return repository.findAllByOrderByCriadoEmDesc().stream().map(IntegracaoDTO.Resumo::de).toList(); }
    @GetMapping("/catalogo")
    public List<IntegracaoCatalogo.Operacao> catalogo(@RequestParam(required = false) IntegracaoModulo modulo) { return catalogo.listar(modulo); }
    @GetMapping("/empresas")
    public List<EmpresaOpcao> empresas() { return empresas.findAll().stream().map(e -> new EmpresaOpcao(e.getIdEmpresa(), e.getNomeFantasia(), e.getCnpj())).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public IntegracaoDTO.Resumo criar(@Valid @RequestBody IntegracaoDTO.Cadastro dto) { return service.salvar(null, dto, usuario.getUserId()); }
    @PutMapping("/{id}")
    public IntegracaoDTO.Resumo editar(@PathVariable String id, @Valid @RequestBody IntegracaoDTO.Cadastro dto) { return service.salvar(id, dto, usuario.getUserId()); }
    @PatchMapping("/{id}/status")
    public IntegracaoDTO.Resumo status(@PathVariable String id, @Valid @RequestBody IntegracaoDTO.AlterarStatus dto) { return service.alterarStatus(id, dto.status(), usuario.getUserId()); }
    @PostMapping("/{id}/rotacionar")
    public IntegracaoDTO.Resumo rotacionar(@PathVariable String id) { return service.rotacionar(id, usuario.getUserId()); }
    @PostMapping("/{id}/reenviar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviar(@PathVariable String id) { service.reenviar(id, usuario.getUserId()); }
    @GetMapping("/{id}/documentacao")
    public ResponseEntity<byte[]> documentacao(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documentacao-integracao-climbe.pdf\"")
                .body(pdf.gerar(service.buscar(id)));
    }
    @GetMapping("/{id}/auditoria")
    public List<IntegracaoAuditoria> auditoria(@PathVariable String id) {
        service.buscar(id); return auditoria.findTop100ByIntegracaoIdOrderByIdDesc(id);
    }
}
