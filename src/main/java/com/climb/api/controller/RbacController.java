package com.climb.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.RbacDTO;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.service.RbacService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/rbac")
public class RbacController {

    private final RbacService rbacService;
    private final UsuarioRepository usuarioRepository;

    public RbacController(RbacService rbacService, UsuarioRepository usuarioRepository) {
        this.rbacService = rbacService;
        this.usuarioRepository = usuarioRepository;
    }

    // Retorna todas as permissões do usuário
    @GetMapping("/usuario/{usuarioId}/permissoes")
    @PreAuthorize("@permissaoAcesso.proprioOuAdministrador(#usuarioId)")
    public ResponseEntity<ApiResponse<Set<PermissaoCodigo>>> getPermissoesDoUsuario(
            @PathVariable Long usuarioId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(rbacService.getPermissoesDoUsuario(usuarioId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    // Verifica se o usuário tem uma permissão específica
    @GetMapping("/usuario/{usuarioId}/tem-permissao/{permissao}")
    @PreAuthorize("@permissaoAcesso.proprioOuAdministrador(#usuarioId)")
    public ResponseEntity<ApiResponse<Boolean>> temPermissao(
            @PathVariable Long usuarioId,
            @PathVariable String permissao) {
        try {
            PermissaoCodigo codigo = PermissaoCodigo.valueOf(permissao);
            return ResponseEntity.ok(ApiResponse.ok(rbacService.temPermissao(usuarioId, codigo)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Permissão inválida: " + permissao));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    // Retorna o perfil completo do usuário
    @GetMapping("/usuario/{usuarioId}/perfil")
    @PreAuthorize("@permissaoAcesso.proprioOuAdministrador(#usuarioId)")
    public ResponseEntity<ApiResponse<RbacDTO>> getPerfilRbac(
            @PathVariable Long usuarioId) {
        try {
            Set<PermissaoCodigo> permissoes = rbacService.getPermissoesDoUsuario(usuarioId);

            var usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            RbacDTO dto = new RbacDTO();
            dto.setUsuarioId(usuarioId);
            dto.setNomeUsuario(usuario.getNomeCompleto());
            dto.setNomeCargo(usuario.getCargo() != null ? usuario.getCargo().getNome() : null);
            dto.setPermissoesEfetivas(permissoes);

            return ResponseEntity.ok(ApiResponse.ok(dto));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}
