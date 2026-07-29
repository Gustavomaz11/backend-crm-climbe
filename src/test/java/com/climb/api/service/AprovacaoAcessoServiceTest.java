package com.climb.api.service;

import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.PermissaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AprovacaoAcessoServiceTest {

    @Mock
    private CargoRepository cargoRepository;

    @Mock
    private PermissaoRepository permissaoRepository;

    @InjectMocks
    private AprovacaoAcessoService service;

    @Test
    void deveRejeitarCargoInativoNaAprovacao() {
        when(cargoRepository.findByIdAndAtivoTrue(99L)).thenReturn(Optional.empty());

        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolver(99L, Set.of(10L)));

        assertEquals("Cargo nao encontrado ou inativo", erro.getMessage());
    }
}
