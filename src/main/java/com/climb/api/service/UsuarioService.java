package com.climb.api.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.climb.api.mapper.UsuarioMapper;
import com.climb.api.model.Cargo;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.Usuario;
import com.climb.api.model.UsuarioOAuth;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import com.climb.api.repository.UsuarioRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final Set<String> SITUACOES_PERMITIDAS_NO_UPDATE =
            Set.of("ATIVO", "INATIVO", "ESPERANDO_APROVACAO");

    private final UsuarioRepository repository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final CargoRepository cargoRepository;
    private final UsuarioMapper usuarioMapper;
    private final OAuth2PendingRegistrationRepository pendingRepository;
    private final UsuarioOAuthRepository usuarioOAuthRepository;

    public UsuarioService(UsuarioRepository repository,
                          EmailService emailService,
                          PasswordEncoder passwordEncoder,
                          CargoRepository cargoRepository,
                          UsuarioMapper usuarioMapper,
                          OAuth2PendingRegistrationRepository pendingRepository,
                          UsuarioOAuthRepository usuarioOAuthRepository) {
        this.repository = repository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.cargoRepository = cargoRepository;
        this.usuarioMapper = usuarioMapper;
        this.pendingRepository = pendingRepository;
        this.usuarioOAuthRepository = usuarioOAuthRepository;
    }

    public Usuario buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    public List<UsuarioResponseDTO> listar() {
        return repository.findAll()
                .stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    public UsuarioResponseDTO buscarPorIdDTO(Long id) {
        return usuarioMapper.toResponse(buscarPorId(id));
    }

    public Usuario buscarPorEmail(String email) {
        return repository.findByEmail(email).orElse(null);
    }

    public Usuario criarViaGoogle(String nomeCompleto, String email, String cpf, String contato, String senha, Long cargoId) {
        exigirCampoObrigatorio(nomeCompleto, "Nome");
        exigirCampoObrigatorio(cpf, "CPF");
        exigirCampoObrigatorio(email, "Email");
        exigirCampoObrigatorio(contato, "Contato");
        exigirCampoObrigatorio(senha, "Senha");
        if (cargoId == null) {
            throw new RuntimeException("Cargo e obrigatorio");
        }

        validarCpfEmailDisponiveis(cpf, email, null);
        Cargo cargo = buscarCargoOuFalhar(cargoId);

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(nomeCompleto);
        usuario.setCpf(cpf);
        usuario.setEmail(email);
        usuario.setContato(contato);
        usuario.setSituacao("ESPERANDO_APROVACAO");
        usuario.setCargo(cargo);
        usuario.setSenhaHash(passwordEncoder.encode(senha));

        Usuario salvo = repository.save(usuario);
        emailService.enviarEmailBoasVindas(salvo.getEmail(), salvo.getNomeCompleto());

        return salvo;
    }

    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        exigirCampoObrigatorio(dto.getNomeCompleto(), "Nome");
        exigirCampoObrigatorio(dto.getCpf(), "CPF");
        exigirCampoObrigatorio(dto.getEmail(), "Email");
        exigirCampoObrigatorio(dto.getSenha(), "Senha");
        if (dto.getCargoId() == null) {
            throw new RuntimeException("Cargo é obrigatório");
        }

        validarCpfEmailDisponiveis(dto.getCpf(), dto.getEmail(), null);
        Cargo cargo = buscarCargoOuFalhar(dto.getCargoId());

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(dto.getNomeCompleto());
        usuario.setCpf(dto.getCpf());
        usuario.setEmail(dto.getEmail());
        usuario.setContato(dto.getContato());
        usuario.setSenhaHash(passwordEncoder.encode(dto.getSenha()));
        usuario.setSituacao("ESPERANDO_APROVACAO");
        usuario.setCargo(cargo);

        return usuarioMapper.toResponse(repository.save(usuario));
    }

    public String criarSolicitacaoAcesso(UsuarioRequestDTO dto) {
        criar(dto);
        return "Solicitação de acesso enviada com sucesso. Aguarde aprovação do administrador.";
    }

    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario usuario = buscarPorId(id);

        validarCpfEmailDisponiveis(dto.getCpf(), dto.getEmail(), id);

        if (dto.getCargoId() != null) {
            usuario.setCargo(buscarCargoOuFalhar(dto.getCargoId()));
        }

        usuario.setNomeCompleto(dto.getNomeCompleto());
        usuario.setCpf(dto.getCpf());
        usuario.setEmail(dto.getEmail());
        usuario.setContato(dto.getContato());

        if (dto.getSituacao() != null) {
            if (!SITUACOES_PERMITIDAS_NO_UPDATE.contains(dto.getSituacao())) {
                throw new RuntimeException("Situação inválida");
            }
            usuario.setSituacao(dto.getSituacao());
        }

        if (dto.getSenha() != null && !dto.getSenha().isEmpty()) {
            usuario.setSenhaHash(passwordEncoder.encode(dto.getSenha()));
        }

        return usuarioMapper.toResponse(repository.save(usuario));
    }

    public void deletar(Long id) {
        repository.delete(buscarPorId(id));
    }

    public UsuarioResponseDTO aprovarUsuario(Long id) {
        Usuario usuario = buscarPorId(id);

        if (!"ESPERANDO_APROVACAO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Usuário não está aguardando aprovação");
        }

        usuario.setSituacao("ATIVO");
        return usuarioMapper.toResponse(repository.save(usuario));
    }

    public List<UsuarioResponseDTO> listarUsuariosPendentes() {
        return repository.findAll()
                .stream()
                .filter(u -> "ESPERANDO_APROVACAO".equals(u.getSituacao()))
                .map(usuarioMapper::toResponse)
                .toList();
    }

    @Transactional
    public UsuarioResponseDTO completarCadastroViaPending(Long pendingId, CompletarCadastroRequestDTO dto) {
        exigirCampoObrigatorio(dto.getCpf(), "CPF");
        exigirCampoObrigatorio(dto.getContato(), "Contato");
        if (dto.getCargoId() == null) {
            throw new RuntimeException("Cargo é obrigatório");
        }

        OAuth2PendingRegistration pending = pendingRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Cadastro pendente não encontrado"));

        if (Boolean.TRUE.equals(pending.getConsumido())) {
            throw new RuntimeException("Cadastro pendente já foi concluído");
        }
        if (!Boolean.TRUE.equals(pending.getAprovado())) {
            throw new RuntimeException("Cadastro pendente ainda não foi aprovado");
        }
        if (pending.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cadastro pendente expirado");
        }

        int linhasAfetadas = pendingRepository.consumirSeNaoConsumido(pendingId);
        if (linhasAfetadas == 0) {
            throw new RuntimeException("Cadastro pendente já está em processamento");
        }

        validarCpfEmailDisponiveis(dto.getCpf(), pending.getEmail(), null);
        Cargo cargo = buscarCargoOuFalhar(dto.getCargoId());

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(pending.getNome() != null && !pending.getNome().isBlank()
                ? pending.getNome()
                : pending.getEmail());
        usuario.setEmail(pending.getEmail());
        usuario.setCpf(dto.getCpf());
        usuario.setContato(dto.getContato());
        usuario.setCargo(cargo);
        usuario.setSituacao("ATIVO");
        usuario.setSenhaHash("GOOGLE_OAUTH_" + UUID.randomUUID());

        Usuario salvo = repository.save(usuario);

        UsuarioOAuth vinculo = new UsuarioOAuth();
        vinculo.setUsuario(salvo);
        vinculo.setProvider(OAuthProvider.GOOGLE);
        vinculo.setProviderUserId(pending.getProviderUserId());
        vinculo.setEmailProvider(pending.getEmail());
        vinculo.setNomeProvider(pending.getNome());
        vinculo.setAvatarUrl(pending.getAvatarUrl());
        vinculo.setVinculadoEm(LocalDateTime.now());
        usuarioOAuthRepository.save(vinculo);

        return usuarioMapper.toResponse(salvo);
    }

    Cargo buscarCargoOuFalhar(Long cargoId) {
        return cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RuntimeException("Cargo não encontrado"));
    }

    private void validarCpfEmailDisponiveis(String cpf, String email, Long usuarioIdAtual) {
        if (cpf != null && !cpf.isBlank()) {
            repository.findByCpf(cpf).ifPresent(u -> {
                if (usuarioIdAtual == null || !u.getId().equals(usuarioIdAtual)) {
                    throw new RuntimeException(usuarioIdAtual == null ? "CPF já cadastrado" : "CPF já em uso");
                }
            });
        }
        if (email != null && !email.isBlank()) {
            repository.findByEmail(email).ifPresent(u -> {
                if (usuarioIdAtual == null || !u.getId().equals(usuarioIdAtual)) {
                    throw new RuntimeException(usuarioIdAtual == null ? "Email já cadastrado" : "Email já em uso");
                }
            });
        }
    }

    private static void exigirCampoObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new RuntimeException(campo + " é obrigatório");
        }
    }
}
