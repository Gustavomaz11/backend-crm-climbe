package com.climb.api.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.climb.api.model.Cargo;
import com.climb.api.model.Usuario;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.model.dto.CompletarCadastroRequestDTO;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;

import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final CargoRepository cargoRepository;
        
    public UsuarioService(UsuarioRepository repository, EmailService emailService, PasswordEncoder passwordEncoder, CargoRepository cargoRepository) {
        this.repository = repository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.cargoRepository = cargoRepository;
    }

    private UsuarioResponseDTO toResponseDTO(Usuario usuario) {
    UsuarioResponseDTO dto = new UsuarioResponseDTO();

        dto.setId(usuario.getId());
        dto.setNomeCompleto(usuario.getNomeCompleto());
        dto.setCpf(usuario.getCpf());
        dto.setEmail(usuario.getEmail());
        dto.setContato(usuario.getContato());
        dto.setSituacao(usuario.getSituacao());

        if (usuario.getCargo() != null) {
            dto.setCargoNome(usuario.getCargo().getNome());
        }

        return dto;
    }

    public Usuario buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    public List<UsuarioResponseDTO> listar() {
        return repository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UsuarioResponseDTO buscarPorIdDTO(Long id) {
        Usuario usuario = buscarPorId(id);
        return toResponseDTO(usuario);
    }

    public Usuario buscarPorEmail(String email) {
        return repository.findByEmail(email).orElse(null);
    }

    public Usuario criarViaGoogle(String nomeCompleto, String email, String cpf, String contato, String senha, Long cargoId) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            throw new RuntimeException("Nome e obrigatorio");
        }

        if (cpf == null || cpf.isBlank()) {
            throw new RuntimeException("CPF e obrigatorio");
        }

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email e obrigatorio");
        }

        if (contato == null || contato.isBlank()) {
            throw new RuntimeException("Contato e obrigatorio");
        }

        if (senha == null || senha.isBlank()) {
            throw new RuntimeException("Senha e obrigatoria");
        }

        if (cargoId == null) {
            throw new RuntimeException("Cargo e obrigatorio");
        }
        
        if (repository.findByCpf(cpf).isPresent()) {
            throw new RuntimeException("CPF ja cadastrado");
        }

        if (repository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email ja cadastrado");
        }

        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new RuntimeException("Cargo nao encontrado"));

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

        if (dto.getNomeCompleto() == null || dto.getNomeCompleto().isEmpty()) {
            throw new RuntimeException("Nome é obrigatório");
        }

        if (dto.getCpf() == null || dto.getCpf().isEmpty()) {
            throw new RuntimeException("CPF é obrigatório");
        }


        if (dto.getEmail() == null || dto.getEmail().isEmpty()) {
            throw new RuntimeException("Email é obrigatório");
        }

        if (dto.getSenha() == null || dto.getSenha().isEmpty()) {
            throw new RuntimeException("Senha é obrigatória");
        }

        if (repository.findByCpf(dto.getCpf()).isPresent()) {
            throw new RuntimeException("CPF já cadastrado");
        }

        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("Email já cadastrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(dto.getNomeCompleto());
        usuario.setCpf(dto.getCpf());
        usuario.setEmail(dto.getEmail());
        usuario.setContato(dto.getContato());

        String senhaHash = passwordEncoder.encode(dto.getSenha());
        usuario.setSenhaHash(senhaHash);

        usuario.setSituacao("ESPERANDO_APROVACAO");

        if (dto.getCargoId() != null) {
            Cargo cargo = cargoRepository.findById(dto.getCargoId())
                    .orElseThrow(() -> new RuntimeException("Cargo não encontrado"));

            usuario.setCargo(cargo);
        } else {
            throw new RuntimeException("Cargo é obrigatório");
        }

        Usuario salvo = repository.save(usuario);

        return toResponseDTO(salvo);
    }

    public String criarSolicitacaoAcesso(UsuarioRequestDTO dto) {
        criar(dto); // Reutiliza a lógica de criação, mas retorna mensagem
        return "Solicitação de acesso enviada com sucesso. Aguarde aprovação do administrador.";
    }

    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {

        Usuario usuario = buscarPorId(id);

        repository.findByCpf(dto.getCpf()).ifPresent(u -> {
            if (!u.getId().equals(id)) {
                throw new RuntimeException("CPF já em uso");
            }
        });

        repository.findByEmail(dto.getEmail()).ifPresent(u -> {
            if (!u.getId().equals(id)) {
                throw new RuntimeException("Email já em uso");
            }
        });

        if (dto.getCargoId() != null) {
            Cargo cargo = cargoRepository.findById(dto.getCargoId())
                    .orElseThrow(() -> new RuntimeException("Cargo não encontrado"));

            usuario.setCargo(cargo);
        }

        usuario.setNomeCompleto(dto.getNomeCompleto());
        usuario.setCpf(dto.getCpf());
        usuario.setEmail(dto.getEmail());
        usuario.setContato(dto.getContato());

        if (dto.getSituacao() != null) {
            if (!dto.getSituacao().equals("ATIVO")
                    && !dto.getSituacao().equals("INATIVO")
                    && !dto.getSituacao().equals("ESPERANDO_APROVACAO")) {
                throw new RuntimeException("Situação inválida");
            }
            usuario.setSituacao(dto.getSituacao());
        }

        if (dto.getSenha() != null && !dto.getSenha().isEmpty()) {
            String senhaHash = passwordEncoder.encode(dto.getSenha());
            usuario.setSenhaHash(senhaHash);
        }

        Usuario atualizado = repository.save(usuario);

        return toResponseDTO(atualizado);
    }

    public void deletar(Long id) {
        Usuario usuario = buscarPorId(id);
        repository.delete(usuario);
    }

    public UsuarioResponseDTO aprovarUsuario(Long id) {
        Usuario usuario = buscarPorId(id);

        if ("ESPERANDO_APROVACAO".equals(usuario.getSituacao())) {
            usuario.setSituacao("ATIVO");
        } else if ("CADASTRO_PENDENTE".equals(usuario.getSituacao())) {
            usuario.setSituacao("COMPLETAR_CADASTRO");
        } else {
            throw new RuntimeException("Usuário não está aguardando aprovação");
        }

        return toResponseDTO(repository.save(usuario));
    }

    public List<UsuarioResponseDTO> listarUsuariosPendentes() {
        return repository.findAll()
                .stream()
                .filter(u -> "ESPERANDO_APROVACAO".equals(u.getSituacao())
                          || "CADASTRO_PENDENTE".equals(u.getSituacao()))
                .map(this::toResponseDTO)
                .toList();
    }

    public UsuarioResponseDTO completarCadastro(Long usuarioId, CompletarCadastroRequestDTO dto) {
        Usuario usuario = buscarPorId(usuarioId);

        if (!"COMPLETAR_CADASTRO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Usuário não está na etapa de completar cadastro");
        }

        if (dto.getCpf() == null || dto.getCpf().isBlank()) {
            throw new RuntimeException("CPF é obrigatório");
        }
        if (dto.getContato() == null || dto.getContato().isBlank()) {
            throw new RuntimeException("Contato é obrigatório");
        }
        if (dto.getCargoId() == null) {
            throw new RuntimeException("Cargo é obrigatório");
        }

        repository.findByCpf(dto.getCpf()).ifPresent(u -> {
            if (!u.getId().equals(usuarioId)) {
                throw new RuntimeException("CPF já cadastrado");
            }
        });

        Cargo cargo = cargoRepository.findById(dto.getCargoId())
                .orElseThrow(() -> new RuntimeException("Cargo não encontrado"));

        usuario.setCpf(dto.getCpf());
        usuario.setContato(dto.getContato());
        usuario.setCargo(cargo);
        usuario.setSituacao("ATIVO");

        return toResponseDTO(repository.save(usuario));
    }
}
