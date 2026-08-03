package com.climb.api.config;

import com.climb.api.model.Usuario;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.service.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveAceitarTokenSomenteQuandoUsuarioContinuaAtivo() throws Exception {
        Usuario usuario = usuario("ATIVO");
        prepararToken(usuario);

        executarFiltro();

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveInvalidarImediatamenteTokenDeUsuarioRevogado() throws Exception {
        Usuario usuario = usuario("REVOGADO");
        prepararToken(usuario);

        executarFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void prepararToken(Usuario usuario) {
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.extractTokenType("token")).thenReturn(JwtUtil.TYPE_ACCESS);
        when(jwtUtil.extractEmail("token")).thenReturn(usuario.getEmail());
        when(jwtUtil.extractUserId("token")).thenReturn(usuario.getId());
        when(usuarioRepository.existsByIdAndSituacao(usuario.getId(), "ATIVO"))
                .thenReturn("ATIVO".equals(usuario.getSituacao()));
    }

    private void executarFiltro() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        new JwtAuthenticationFilter(jwtUtil, usuarioRepository)
                .doFilterInternal(request, new MockHttpServletResponse(), filterChain);
    }

    private Usuario usuario(String situacao) {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("usuario@climbe.com.br");
        usuario.setSituacao(situacao);
        return usuario;
    }
}
