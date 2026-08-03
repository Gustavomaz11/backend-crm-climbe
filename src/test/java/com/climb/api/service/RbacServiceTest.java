package com.climb.api.service;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final RbacService service = new RbacService(usuarioRepository);

    @Test
    void deveCarregarPermissoesComUmaUnicaConsultaEscalar() {
        when(usuarioRepository.findCodigosPermissoesById(7L)).thenReturn(List.of(
                PermissaoCodigo.COMERCIAL.name(),
                PermissaoCodigo.PROPOSTA_CRUD.name(),
                "CODIGO_LEGADO_INVALIDO"));

        assertEquals(
                java.util.Set.of(PermissaoCodigo.COMERCIAL, PermissaoCodigo.PROPOSTA_CRUD),
                service.getPermissoesDoUsuario(7L));
        verify(usuarioRepository).findCodigosPermissoesById(7L);
    }

    @Test
    void deveManterErroParaUsuarioInexistente() {
        when(usuarioRepository.findCodigosPermissoesById(99L)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.getPermissoesDoUsuario(99L));
    }

    @Test
    void devePermitirUsuarioExistenteSemPermissoes() {
        when(usuarioRepository.findCodigosPermissoesById(8L)).thenReturn(List.of(""));

        assertTrue(service.getPermissoesDoUsuario(8L).isEmpty());
    }
}
