package com.climb.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.service.RbacService;
import com.climb.api.service.UsuarioService;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;
    private final RbacService rbacService;

    public UsuarioController(UsuarioService service, RbacService rbacService) {
        this.service = service;
        this.rbacService = rbacService;
    }

    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/pendentes")
    public List<UsuarioResponseDTO> listarUsuariosPendentes() {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        return service.listarUsuariosPendentes();
    }

    @GetMapping("/{id}")
    public UsuarioResponseDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorIdDTO(id);
    }

    @PostMapping
    public String criar(@RequestBody UsuarioRequestDTO dto) {
        return service.criarSolicitacaoAcesso(dto);
    }

    @PutMapping("/{id}")
    public UsuarioResponseDTO atualizar(@PathVariable Long id,
                                        @RequestBody UsuarioRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        service.deletar(id);
    }

    @PostMapping("/{id}/aprovar")
    public UsuarioResponseDTO aprovarUsuario(@PathVariable Long id) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        return service.aprovarUsuario(id);
    }

    @PostMapping("/completar-cadastro")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> completarCadastro(
            @RequestBody CompletarCadastroRequestDTO dto) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getDetails();
            return ResponseEntity.ok(ApiResponse.ok(service.completarCadastro(userId, dto), "Cadastro concluído com sucesso"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void exigirPermissao(PermissaoCodigo permissao) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!rbacService.temPermissao(userId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão: " + permissao);
        }
    }
}