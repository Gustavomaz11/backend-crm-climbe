package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.GrupoCargo;
import com.climb.api.model.dto.GrupoCargoRequestDTO;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.GrupoCargoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrupoCargoServiceTest {

    @Mock
    private GrupoCargoRepository repository;

    @Mock
    private CargoRepository cargoRepository;

    @InjectMocks
    private GrupoCargoService service;

    @Test
    void deveCriarGrupoNormalizandoOsDados() {
        when(repository.existsByNomeIgnoreCase("Tecnologia")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(GrupoCargo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GrupoCargo criado = service.criar(new GrupoCargoRequestDTO("  Tecnologia  ", "  Produto e TI  "));

        assertEquals("Tecnologia", criado.getNome());
        assertEquals("Produto e TI", criado.getDescricao());
    }

    @Test
    void deveImpedirGrupoComNomeDuplicado() {
        when(repository.existsByNomeIgnoreCase("Comercial")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> service.criar(new GrupoCargoRequestDTO("Comercial", null)));
    }

    @Test
    void deveVincularCargoAoGrupoAtivo() {
        Cargo cargo = cargo(10L, null);
        GrupoCargo grupo = grupo(2L, "Marketing");
        when(cargoRepository.findByIdAndAtivoTrue(10L)).thenReturn(Optional.of(cargo));
        when(repository.findByIdAndAtivoTrue(2L)).thenReturn(Optional.of(grupo));
        when(cargoRepository.save(cargo)).thenReturn(cargo);

        Cargo atualizado = service.vincularCargo(10L, 2L);

        assertEquals(2L, atualizado.getGrupoId());
    }

    @Test
    void deveDesativarGrupoSemExcluirSeusCargos() {
        GrupoCargo grupo = grupo(2L, "Marketing");
        Cargo cargo = cargo(10L, 2L);
        when(repository.findByIdAndAtivoTrue(2L)).thenReturn(Optional.of(grupo));
        when(cargoRepository.findAllByGrupoIdAndAtivoTrue(2L)).thenReturn(List.of(cargo));

        service.desativar(2L);

        assertFalse(grupo.isAtivo());
        assertNull(cargo.getGrupoId());
        verify(cargoRepository).saveAll(List.of(cargo));
        verify(repository).save(grupo);
    }

    private GrupoCargo grupo(Long id, String nome) {
        GrupoCargo grupo = new GrupoCargo();
        grupo.setId(id);
        grupo.setNome(nome);
        return grupo;
    }

    private Cargo cargo(Long id, Long grupoId) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setGrupoId(grupoId);
        cargo.setAtivo(true);
        return cargo;
    }
}
