package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.Permissao;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.PermissaoRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.mail.host=localhost"})
@ActiveProfiles("test")
@Transactional
class GoogleAccessLifecycleIntegrationTest {
    @Autowired private OAuth2PendingService pendingService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private GoogleOAuthService googleService;
    @Autowired private AuthenticationService authenticationService;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CargoRepository cargos;
    @Autowired private PermissaoRepository permissoes;
    @Autowired private OAuth2PendingRegistrationRepository pendings;
    @Autowired private UsuarioOAuthRepository vinculos;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;

    @Test
    void deveGerenciarAprovadoAntesDoPrimeiroAcessoEConcluirOMesmoUsuario() {
        Cargo cargo = salvarCargo("Analista Comercial");
        Cargo novoCargo = salvarCargo("Diretor Comercial");
        Permissao permissao = salvarPermissao("CONTRATO_CRUD");
        Permissao novaPermissao = salvarPermissao("PROPOSTA_CRUD");
        var pending = pendingService.criarPendingGoogle("google-nathan", "nathan@test.com", "Nathan", null);
        pendingService.aprovar(pending.getId(), null, cargo.getId(), Set.of(permissao.getIdPermissao()));

        Usuario usuario = usuarios.findByEmail(pending.getEmail()).orElseThrow();
        Long usuarioId = usuario.getId();
        assertEquals("COMPLETAR_CADASTRO", usuario.getSituacao());
        assertNull(usuario.getCpf());
        assertTrue(usuarioService.listar().stream().anyMatch(item -> item.getId().equals(usuarioId)));
        assertTrue(usuarioService.listarAcessosGerenciaveis().stream().anyMatch(item -> item.getId().equals(usuarioId)));
        assertFalse(authenticationService.gerarRespostaLogin(usuario).isSuccess());

        usuarioService.alterarCargo(usuarioId, novoCargo.getId());
        usuario.getPermissoes().clear();
        usuario.getPermissoes().add(novaPermissao);
        usuarios.saveAndFlush(usuario);
        pending.setExpiraEm(LocalDateTime.now().minusDays(1));
        pendings.saveAndFlush(pending);
        var primeiroLogin = googleService.resolverLoginGoogle("google-nathan", pending.getEmail(), "Nathan", null);
        assertEquals(GoogleOAuthService.STATUS_COMPLETAR_CADASTRO, primeiroLogin.getStatus());
        assertNotNull(primeiroLogin.getPendingToken());

        CompletarCadastroRequestDTO dto = new CompletarCadastroRequestDTO();
        dto.setCpf("12345678901");
        dto.setContato("79999999999");
        dto.setSenha("senha-segura");
        var concluido = usuarioService.completarCadastroViaPending(pending.getId(), dto);
        usuarios.flush();
        assertEquals(usuarioId, concluido.getId());
        assertEquals("ATIVO", concluido.getSituacao());
        assertEquals(novoCargo.getId(), usuario.getCargo().getId());
        assertEquals(Set.of(novaPermissao), usuario.getPermissoes());
        assertTrue(passwordEncoder.matches(dto.getSenha(), usuario.getSenhaHash()));
        assertEquals(usuarioId, vinculos.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-nathan")
                .orElseThrow().getUsuario().getId());
        assertTrue(pendings.findById(pending.getId()).orElseThrow().getConsumido());
        assertTrue(authenticationService.autenticar(pending.getEmail(), dto.getSenha()).isSuccess());
        assertEquals(GoogleOAuthService.STATUS_LOGIN_SUCCESS,
                googleService.resolverLoginGoogle("google-nathan", pending.getEmail(), "Nathan", null).getStatus());
        assertThrows(RuntimeException.class, () -> usuarioService.completarCadastroViaPending(pending.getId(), dto));
    }

    @Test
    void deveLogarUsuarioManualAtivoMesmoComSolicitacaoGoogleAprovadaAberta() {
        Cargo cargoAtual = salvarCargo("Diretor Comercial");
        Cargo cargoSolicitado = salvarCargo("Analista Comercial");
        Permissao permissaoAtual = salvarPermissao("PERMITIR_ACESSO");
        Permissao permissaoSolicitada = salvarPermissao("CONTRATO_CRUD");
        var pending = pendingService.criarPendingGoogle("google-existente", "existente@test.com", "Existente", null);
        Usuario existente = new Usuario();
        existente.setNomeCompleto("Existente");
        existente.setEmail(pending.getEmail());
        existente.setCpf("12345678901");
        existente.setContato("79999999999");
        existente.setSenhaHash(passwordEncoder.encode("senha-segura"));
        existente.setSituacao("ATIVO");
        existente.setCargo(cargoAtual);
        existente.getPermissoes().add(permissaoAtual);
        usuarios.saveAndFlush(existente);
        pendingService.aprovar(pending.getId(), null, cargoSolicitado.getId(), Set.of(permissaoSolicitada.getIdPermissao()));

        var login = googleService.resolverLoginGoogle("google-existente", existente.getEmail(), "Existente", null);

        assertEquals(GoogleOAuthService.STATUS_LOGIN_SUCCESS, login.getStatus());
        assertEquals(existente.getId(), login.getLogin().getUsuario().getId());
        assertSame(cargoAtual, existente.getCargo());
        assertEquals(Set.of(permissaoAtual), existente.getPermissoes());
        assertTrue(pending.getConsumido());
    }

    private Cargo salvarCargo(String nome) {
        Cargo cargo = new Cargo();
        cargo.setNome(nome);
        return cargos.saveAndFlush(cargo);
    }

    private Permissao salvarPermissao(String codigo) {
        Permissao permissao = new Permissao();
        permissao.setCodigo(codigo);
        permissao.setDescricao(codigo);
        return permissoes.saveAndFlush(permissao);
    }
}
