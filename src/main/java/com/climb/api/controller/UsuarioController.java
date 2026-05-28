package com.climb.api.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.climb.api.config.PendingPrincipal;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.model.dto.UsuarioPendenteResponseDTO;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.service.OAuth2PendingService;
import com.climb.api.service.RbacService;
import com.climb.api.service.UsuarioService;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;
    private final RbacService rbacService;
    private final OAuth2PendingService pendingService;

    public UsuarioController(UsuarioService service,
                             RbacService rbacService,
                             OAuth2PendingService pendingService) {
        this.service = service;
        this.rbacService = rbacService;
        this.pendingService = pendingService;
    }

    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/pendentes")
    public List<UsuarioPendenteResponseDTO> listarUsuariosPendentes() {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);

        List<UsuarioPendenteResponseDTO> resultado = new ArrayList<>();

        for (UsuarioResponseDTO u : service.listarUsuariosPendentes()) {
            UsuarioPendenteResponseDTO item = new UsuarioPendenteResponseDTO();
            item.setOrigem("USUARIO");
            item.setId(u.getId());
            item.setNomeCompleto(u.getNomeCompleto());
            item.setEmail(u.getEmail());
            item.setCpf(u.getCpf());
            item.setContato(u.getContato());
            item.setCargoNome(u.getCargoNome());
            resultado.add(item);
        }

        for (OAuth2PendingRegistration p : pendingService.listarPendentes()) {
            UsuarioPendenteResponseDTO item = new UsuarioPendenteResponseDTO();
            item.setOrigem("GOOGLE");
            item.setId(p.getId());
            item.setNomeCompleto(p.getNome());
            item.setEmail(p.getEmail());
            item.setAvatarUrl(p.getAvatarUrl());
            item.setCriadoEm(p.getCriadoEm());
            item.setExpiraEm(p.getExpiraEm());
            resultado.add(item);
        }

        return resultado;
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

    @PostMapping("/pendentes-google/{pendingId}/aprovar")
    public ResponseEntity<ApiResponse<Void>> aprovarPendingGoogle(@PathVariable Long pendingId) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        Long aprovadorId = (Long) SecurityContextHolder.getContext().getAuthentication().getDetails();
        try {
            pendingService.aprovar(pendingId, aprovadorId);
            return ResponseEntity.ok(ApiResponse.ok(null, "Cadastro Google aprovado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/completar-cadastro")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> completarCadastro(
            @RequestBody CompletarCadastroRequestDTO dto) {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof PendingPrincipal pp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Token invalido para completar cadastro"));
        }
        try {
            UsuarioResponseDTO resultado = service.completarCadastroViaPending(pp.pendingId(), dto);
            return ResponseEntity.ok(ApiResponse.ok(resultado, "Cadastro concluído com sucesso"));
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
