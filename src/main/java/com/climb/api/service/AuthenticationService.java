package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UsuarioService usuarioService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponseDTO autenticar(String email, String senha) {
        Usuario usuario = usuarioService.buscarPorEmail(email);
        validarUsuarioAtivo(usuario, "Email ou senha invalidos");

        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new RuntimeException("Senha invalida");
        }

        return gerarRespostaLogin(usuario);
    }

    public LoginResponseDTO autenticarComGoogle(String email) {
        Usuario usuario = usuarioService.buscarPorEmail(email);
        validarUsuarioAtivo(usuario, "Usuario nao cadastrado para login com Google");
        return gerarRespostaLogin(usuario);
    }

    public String refreshAccessToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh token invalido ou expirado");
        }

        String tokenType = jwtUtil.extractTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Token fornecido nao e um refresh token");
        }

        Long usuarioId = jwtUtil.extractUserId(refreshToken);
        String email = jwtUtil.extractEmail(refreshToken);

        return jwtUtil.generateAccessToken(usuarioId, email);
    }

    private UsuarioResponseDTO buildUsuarioResponseDTO(Usuario usuario) {
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

    public void validarUsuarioAtivo(Usuario usuario, String usuarioNaoEncontradoMessage) {
        if (usuario == null) {
            throw new RuntimeException(usuarioNaoEncontradoMessage);
        }

        if ("CADASTRO_PENDENTE".equals(usuario.getSituacao())) {
            throw new RuntimeException("Sua conta esta aguardando aprovacao do administrador");
        }

        if ("ESPERANDO_APROVACAO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Sua solicitacao de acesso esta pendente de aprovacao do administrador");
        }

        if ("COMPLETAR_CADASTRO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Sua conta foi aprovada. Complete seu cadastro para acessar o sistema");
        }

        if ("INATIVO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Sua conta foi desativada. Entre em contato com o administrador");
        }

        if (!"ATIVO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Usuario com situacao invalida");
        }
    }

    public LoginResponseDTO gerarRespostaLoginSemValidacao(Usuario usuario) {
        String accessToken = jwtUtil.generateAccessToken(usuario.getId(), usuario.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(usuario.getId(), usuario.getEmail());
        UsuarioResponseDTO dto = buildUsuarioResponseDTO(usuario);
        return new LoginResponseDTO(accessToken, refreshToken, dto, jwtUtil.getAccessTokenExpirationTime());
    }

    public LoginResponseDTO gerarRespostaLogin(Usuario usuario) {
        validarUsuarioAtivo(usuario, "Usuario nao encontrado");
        String accessToken = jwtUtil.generateAccessToken(usuario.getId(), usuario.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(usuario.getId(), usuario.getEmail());
        UsuarioResponseDTO usuarioDTO = buildUsuarioResponseDTO(usuario);
        long expiresIn = jwtUtil.getAccessTokenExpirationTime();

        return new LoginResponseDTO(accessToken, refreshToken, usuarioDTO, expiresIn);
    }
}
