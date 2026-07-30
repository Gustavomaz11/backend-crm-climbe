package com.climb.api.service;

import com.climb.api.mapper.UsuarioMapper;
import com.climb.api.model.Cargo;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.Permissao;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.PermissaoRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceAprovacaoTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CargoRepository cargoRepository;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private OAuth2PendingRegistrationRepository pendingRepository;
    @Mock private UsuarioOAuthRepository usuarioOAuthRepository;
    @Mock private PermissaoRepository permissaoRepository;
    @Mock private AprovacaoAcessoService aprovacaoAcessoService;
    @Mock private SolicitacaoAcessoService solicitacaoAcessoService;
    @Mock private GoogleCredentialService googleCredentialService;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(
                usuarioRepository,
                emailService,
                passwordEncoder,
                cargoRepository,
                usuarioMapper,
                pendingRepository,
                usuarioOAuthRepository,
                permissaoRepository,
                aprovacaoAcessoService,
                solicitacaoAcessoService,
                googleCredentialService);
    }

    @Test
    void deveAplicarCargoEPermissoesDefinidosNaAprovacaoGoogle() {
        Cargo cargo = cargo(2L, "CEO");
        Permissao permitirAcesso = permissao(10L, "PERMITIR_ACESSO");
        OAuth2PendingRegistration pending = pendingAprovado(cargo, Set.of(permitirAcesso));
        CompletarCadastroRequestDTO request = new CompletarCadastroRequestDTO();
        request.setCpf("07508154509");
        request.setContato("gustavo@climbe.com.br");

        when(pendingRepository.findById(42L)).thenReturn(Optional.of(pending));
        when(pendingRepository.consumirSeNaoConsumido(42L)).thenReturn(1);
        when(usuarioRepository.findByCpf(request.getCpf())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail(pending.getEmail())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(7L);
            return usuario;
        });
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(new UsuarioResponseDTO());

        service.completarCadastroViaPending(42L, request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario usuarioSalvo = usuarioCaptor.getValue();
        assertEquals(cargo, usuarioSalvo.getCargo());
        assertEquals(Set.of(permitirAcesso), usuarioSalvo.getPermissoes());
    }

    @Test
    void deveEnviarEmailDeEsperaAoCriarSolicitacaoManual() {
        Cargo cargo = cargo(2L, "Analista Comercial");
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNomeCompleto("Novo Usuario");
        request.setCpf("07508154509");
        request.setEmail("novo.usuario@gmail.com");
        request.setContato("79999999999");
        request.setSenha("senha-segura");
        request.setCargoId(cargo.getId());

        when(usuarioRepository.findByCpf(request.getCpf())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(cargoRepository.findById(cargo.getId())).thenReturn(Optional.of(cargo));
        when(permissaoRepository.findByCodigo(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getSenha())).thenReturn("senha-hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(8L);
            return usuario;
        });
        when(usuarioMapper.toResponse(any(Usuario.class))).thenReturn(new UsuarioResponseDTO());

        service.criar(request);

        verify(emailService).enviarAguardandoAprovacao(request.getEmail(), request.getNomeCompleto());
    }

    @Test
    void deveEnviarEmailAoAprovarSolicitacaoManual() {
        Usuario pendente = new Usuario();
        pendente.setId(8L);
        pendente.setNomeCompleto("Novo Usuario");
        pendente.setEmail("novo@climbe.com.br");
        pendente.setSituacao("ESPERANDO_APROVACAO");
        Cargo cargo = cargo(2L, "Analista Comercial");
        AprovacaoAcessoService.AtribuicaoAcesso atribuicao =
                new AprovacaoAcessoService.AtribuicaoAcesso(cargo, Set.of());

        when(usuarioRepository.findById(8L)).thenReturn(Optional.of(pendente));
        when(aprovacaoAcessoService.resolver(cargo.getId(), Set.of())).thenReturn(atribuicao);
        when(usuarioRepository.save(pendente)).thenReturn(pendente);
        when(usuarioMapper.toResponse(pendente)).thenReturn(new UsuarioResponseDTO());

        service.aprovarUsuario(8L, 1L, cargo.getId(), Set.of());

        verify(emailService).enviarAcessoAprovado(pendente.getEmail(), pendente.getNomeCompleto());
    }

    private OAuth2PendingRegistration pendingAprovado(Cargo cargo, Set<Permissao> permissoes) {
        OAuth2PendingRegistration pending = new OAuth2PendingRegistration();
        pending.setId(42L);
        pending.setProvider(OAuthProvider.GOOGLE);
        pending.setProviderUserId("google-sub");
        pending.setEmail("gustavo@climbe.com.br");
        pending.setNome("Gustavo");
        pending.setAprovado(true);
        pending.setConsumido(false);
        pending.setExpiraEm(LocalDateTime.now().plusDays(1));
        pending.setCargo(cargo);
        pending.setPermissoes(permissoes);
        return pending;
    }

    private Cargo cargo(Long id, String nome) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setNome(nome);
        return cargo;
    }

    private Permissao permissao(Long id, String codigo) {
        Permissao permissao = new Permissao();
        permissao.setIdPermissao(id);
        permissao.setCodigo(codigo);
        permissao.setDescricao(codigo);
        return permissao;
    }
}
