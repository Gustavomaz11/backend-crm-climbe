package com.climb.api.service;

import com.climb.api.mapper.UsuarioMapper;
import com.climb.api.model.AuthStatus;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.AuthResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UsuarioService usuarioService;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioMapper usuarioMapper;

    @Test
    void naoDeveRenovarTokenDeUsuarioRevogado() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("usuario@climbe.com.br");
        usuario.setSituacao("REVOGADO");
        when(jwtUtil.validateToken("refresh-token")).thenReturn(true);
        when(jwtUtil.extractTokenType("refresh-token")).thenReturn(JwtUtil.TYPE_REFRESH);
        when(jwtUtil.extractUserId("refresh-token")).thenReturn(7L);
        when(usuarioService.buscarPorId(7L)).thenReturn(usuario);

        AuthenticationService service = new AuthenticationService(
                usuarioService, jwtUtil, passwordEncoder, usuarioMapper);
        AuthResult<String> resultado = service.refreshAccessToken("refresh-token");

        assertEquals(AuthStatus.INATIVO, resultado.status());
        verify(jwtUtil, never()).generateAccessToken(7L, usuario.getEmail());
    }
}
