package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.model.dto.AtualizarMeuPerfilRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioService usuarioService;
    @Mock private CloudflareR2ArquivoStorageService storageService;

    @Test
    void deveAtualizarSomenteDadosPessoaisSemAlterarCargoOuSituacao() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setSituacao("ATIVO");
        when(usuarioService.buscarPorId(7L)).thenReturn(usuario);
        when(usuarioRepository.findByEmail("novo@empresa.com")).thenReturn(Optional.empty());
        when(usuarioRepository.findByCpf("12345678900")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarioService.toResponse(usuario)).thenReturn(new UsuarioResponseDTO());

        PerfilService service = new PerfilService(usuarioRepository, usuarioService, storageService);
        service.atualizar(7L, new AtualizarMeuPerfilRequestDTO(
                "Novo Nome", "NOVO@EMPRESA.COM", "12345678900", "79999999999"));

        assertEquals("Novo Nome", usuario.getNomeCompleto());
        assertEquals("novo@empresa.com", usuario.getEmail());
        assertEquals("ATIVO", usuario.getSituacao());
    }
}
