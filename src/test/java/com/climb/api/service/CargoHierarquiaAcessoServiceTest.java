package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.Usuario;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoHierarquiaAcessoServiceTest {

    @Mock private CargoRepository cargoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    private CargoHierarquiaAcessoService service;

    @BeforeEach
    void setUp() {
        service = new CargoHierarquiaAcessoService(cargoRepository, usuarioRepository);
    }

    @Test
    void deveIncluirUsuarioAtualEUsuariosDeTodosOsCargosDescendentes() {
        Cargo diretorTi = cargo(1L, null);
        Cargo coordenadorTi = cargo(2L, 1L);
        Cargo analistaTi = cargo(3L, 2L);
        Cargo diretorMarketing = cargo(4L, null);
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setCargo(diretorTi);

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(cargoRepository.findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc())
                .thenReturn(List.of(diretorTi, coordenadorTi, analistaTi, diretorMarketing));
        when(usuarioRepository.findIdsBySituacaoAndCargoIds("ATIVO", Set.of(2L, 3L)))
                .thenReturn(Set.of(20L, 30L));

        Set<Long> resultado = service.buscarUsuariosVisiveis(10L);

        assertEquals(Set.of(10L, 20L, 30L), resultado);
    }

    private Cargo cargo(Long id, Long superiorId) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setCargoSuperiorId(superiorId);
        cargo.setAtivo(true);
        cargo.setNome("Cargo " + id);
        return cargo;
    }
}
