package com.climb.api.service;

import com.climb.api.mapper.UsuarioMapper;
import com.climb.api.model.AuthStatus;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    public AuthenticationService(UsuarioService usuarioService,
                                 JwtUtil jwtUtil,
                                 PasswordEncoder passwordEncoder,
                                 UsuarioMapper usuarioMapper) {
        this.usuarioService = usuarioService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
    }

    public AuthResult<LoginResponseDTO> autenticar(String email, String senha) {
        Usuario usuario = usuarioService.buscarPorEmail(email);
        AuthResult<Void> situacao = validarUsuarioAtivo(usuario, "Email ou senha invalidos");
        if (!situacao.isSuccess()) {
            AuthStatus status = situacao.status() == AuthStatus.USER_NOT_FOUND
                    ? AuthStatus.INVALID_CREDENTIALS
                    : situacao.status();
            return AuthResult.failure(status, situacao.message());
        }

        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            return AuthResult.failure(AuthStatus.INVALID_CREDENTIALS, "Email ou senha invalidos");
        }

        return AuthResult.success(buildLoginResponse(usuario));
    }

    public AuthResult<LoginResponseDTO> autenticarComGoogle(String email) {
        Usuario usuario = usuarioService.buscarPorEmail(email);
        AuthResult<Void> situacao = validarUsuarioAtivo(usuario, "Usuario nao cadastrado para login com Google");
        if (!situacao.isSuccess()) {
            return AuthResult.failure(situacao.status(), situacao.message());
        }
        return AuthResult.success(buildLoginResponse(usuario));
    }

    public AuthResult<String> refreshAccessToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            return AuthResult.failure(AuthStatus.INVALID_REFRESH_TOKEN, "Refresh token invalido ou expirado");
        }

        String tokenType = jwtUtil.extractTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            return AuthResult.failure(AuthStatus.WRONG_TOKEN_TYPE, "Token fornecido nao e um refresh token");
        }

        Long usuarioId = jwtUtil.extractUserId(refreshToken);
        String email = jwtUtil.extractEmail(refreshToken);

        return AuthResult.success(jwtUtil.generateAccessToken(usuarioId, email));
    }

    public long getAccessTokenExpirationTime() {
        return jwtUtil.getAccessTokenExpirationTime();
    }

    public AuthResult<Void> validarUsuarioAtivo(Usuario usuario, String usuarioNaoEncontradoMessage) {
        if (usuario == null) {
            return AuthResult.failure(AuthStatus.USER_NOT_FOUND, usuarioNaoEncontradoMessage);
        }

        return switch (String.valueOf(usuario.getSituacao())) {
            case "CADASTRO_PENDENTE" -> AuthResult.failure(
                    AuthStatus.PENDING_APPROVAL,
                    "Sua conta esta aguardando aprovacao do administrador");
            case "ESPERANDO_APROVACAO" -> AuthResult.failure(
                    AuthStatus.PENDING_APPROVAL,
                    "Sua solicitacao de acesso esta pendente de aprovacao do administrador");
            case "COMPLETAR_CADASTRO" -> AuthResult.failure(
                    AuthStatus.COMPLETAR_CADASTRO,
                    "Sua conta foi aprovada. Complete seu cadastro para acessar o sistema");
            case "INATIVO" -> AuthResult.failure(
                    AuthStatus.INATIVO,
                    "Sua conta foi desativada. Entre em contato com o administrador");
            case "ATIVO" -> AuthResult.success(null);
            default -> AuthResult.failure(AuthStatus.SITUACAO_INVALIDA, "Usuario com situacao invalida");
        };
    }

    public LoginResponseDTO gerarRespostaLoginSemValidacao(Usuario usuario) {
        return buildLoginResponse(usuario);
    }

    public AuthResult<LoginResponseDTO> gerarRespostaLogin(Usuario usuario) {
        AuthResult<Void> situacao = validarUsuarioAtivo(usuario, "Usuario nao encontrado");
        if (!situacao.isSuccess()) {
            return AuthResult.failure(situacao.status(), situacao.message());
        }
        return AuthResult.success(buildLoginResponse(usuario));
    }

    private LoginResponseDTO buildLoginResponse(Usuario usuario) {
        String accessToken = jwtUtil.generateAccessToken(usuario.getId(), usuario.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(usuario.getId(), usuario.getEmail());
        UsuarioResponseDTO usuarioDTO = usuarioMapper.toResponse(usuario);
        long expiresIn = jwtUtil.getAccessTokenExpirationTime();
        return new LoginResponseDTO(accessToken, refreshToken, usuarioDTO, expiresIn);
    }
}
