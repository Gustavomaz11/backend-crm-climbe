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
import com.climb.api.model.dto.AlterarCargoRequestDTO;
import com.climb.api.model.dto.AprovarAcessoRequestDTO;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.UsuarioPendenteResponseDTO;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.service.OAuth2PendingService;
import com.climb.api.service.AuthenticationService;
import com.climb.api.service.RbacService;
import com.climb.api.service.SolicitacaoAcessoService;
import com.climb.api.service.UsuarioService;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;
    private final RbacService rbacService;
    private final OAuth2PendingService pendingService;
    private final AuthenticationService authenticationService;
    private final SolicitacaoAcessoService solicitacaoAcessoService;

    public UsuarioController(UsuarioService service,
                             RbacService rbacService,
                             OAuth2PendingService pendingService,
                             AuthenticationService authenticationService,
                             SolicitacaoAcessoService solicitacaoAcessoService) {
        this.service = service;
        this.rbacService = rbacService;
        this.pendingService = pendingService;
        this.authenticationService = authenticationService;
        this.solicitacaoAcessoService = solicitacaoAcessoService;
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

    @GetMapping("/solicitacoes")
    public List<UsuarioPendenteResponseDTO> listarSolicitacoesAcesso() {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        return solicitacaoAcessoService.listar();
    }

    @GetMapping("/acessos")
    public List<UsuarioResponseDTO> listarAcessosGerenciaveis() {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        return service.listarAcessosGerenciaveis();
    }

    @GetMapping("/{id}")
    public UsuarioResponseDTO buscarPorId(@PathVariable Long id) {
        return service.buscarPorIdDTO(id);
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody UsuarioRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.criarSolicitacaoAcesso(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public UsuarioResponseDTO atualizar(@PathVariable Long id,
                                        @RequestBody UsuarioRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        service.deletar(id, usuarioAutenticadoId());
    }

    @PostMapping("/{id}/aprovar")
    public UsuarioResponseDTO aprovarUsuario(@PathVariable Long id,
                                             @RequestBody AprovarAcessoRequestDTO dto) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        return service.aprovarUsuario(id, usuarioAutenticadoId(), dto.cargoId(), dto.permissaoIds());
    }

    @PostMapping("/{id}/recusar")
    public ResponseEntity<ApiResponse<Void>> recusarUsuario(@PathVariable Long id) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        service.recusarUsuario(id, usuarioAutenticadoId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Solicitacao de acesso recusada"));
    }

    @PostMapping("/{id}/revogar")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> revogarAcesso(@PathVariable Long id) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        UsuarioResponseDTO usuario = service.revogarAcesso(id, usuarioAutenticadoId());
        return ResponseEntity.ok(ApiResponse.ok(usuario, "Acesso revogado"));
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> reativarAcesso(@PathVariable Long id) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        UsuarioResponseDTO usuario = service.reativarAcesso(id);
        return ResponseEntity.ok(ApiResponse.ok(usuario, "Acesso reativado"));
    }

    @PatchMapping("/{id}/cargo")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> alterarCargo(
            @PathVariable Long id,
            @RequestBody AlterarCargoRequestDTO dto) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        UsuarioResponseDTO usuario = service.alterarCargo(id, dto.cargoId());
        return ResponseEntity.ok(ApiResponse.ok(usuario, "Cargo atualizado"));
    }

    @PostMapping("/pendentes-google/{pendingId}/aprovar")
    public ResponseEntity<ApiResponse<Void>> aprovarPendingGoogle(
            @PathVariable Long pendingId,
            @RequestBody AprovarAcessoRequestDTO dto) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        Long aprovadorId = usuarioAutenticadoId();
        try {
            pendingService.aprovar(pendingId, aprovadorId, dto.cargoId(), dto.permissaoIds());
            return ResponseEntity.ok(ApiResponse.ok(null, "Cadastro Google aprovado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/pendentes-google/{pendingId}/recusar")
    public ResponseEntity<ApiResponse<Void>> recusarPendingGoogle(@PathVariable Long pendingId) {
        exigirPermissao(PermissaoCodigo.PERMITIR_ACESSO);
        Long aprovadorId = usuarioAutenticadoId();
        try {
            pendingService.recusar(pendingId, aprovadorId);
            return ResponseEntity.ok(ApiResponse.ok(null, "Cadastro Google recusado"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/completar-cadastro")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> completarCadastro(
            @RequestBody CompletarCadastroRequestDTO dto) {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof PendingPrincipal pp)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Token invalido para completar cadastro"));
        }
        try {
            UsuarioResponseDTO resultado = service.completarCadastroViaPending(pp.pendingId(), dto);
            AuthResult<LoginResponseDTO> login = authenticationService.autenticarComGoogle(resultado.getEmail());
            if (!login.isSuccess()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(login.message()));
            }
            return ResponseEntity.ok(ApiResponse.ok(login.data(), "Cadastro concluído com sucesso"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void exigirPermissao(PermissaoCodigo permissao) {
        Long userId = usuarioAutenticadoId();
        if (!rbacService.temPermissao(userId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão: " + permissao);
        }
    }

    private Long usuarioAutenticadoId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (details instanceof Number id) {
            return id.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }
}
