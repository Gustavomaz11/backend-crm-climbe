package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.dto.CargoHierarquiaItemRequestDTO;
import com.climb.api.model.dto.CargoHierarquiaRequestDTO;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.GrupoCargoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoServiceTest {

    @Mock
    private CargoRepository repository;

    @Mock
    private GrupoCargoRepository grupoRepository;

    @InjectMocks
    private CargoService service;

    @Test
    void deveListarSomenteCargosAtivosEmOrdemAlfabetica() {
        Cargo cargo = cargo("Analista Comercial");
        when(repository.findAllByAtivoTrueOrderByNomeAsc()).thenReturn(List.of(cargo));

        List<Cargo> resultado = service.listar();

        assertEquals(List.of(cargo), resultado);
        verify(repository).findAllByAtivoTrueOrderByNomeAsc();
    }

    @Test
    void deveCriarCargoComoAtivo() {
        Cargo cargo = cargo("Novo cargo");
        cargo.setAtivo(false);
        when(repository.save(cargo)).thenReturn(cargo);

        Cargo criado = service.criar(cargo);

        assertTrue(criado.isAtivo());
        verify(repository).save(cargo);
    }

    @Test
    void deveAtualizarUmaHierarquiaValida() {
        Cargo diretor = cargo(1L, "Diretor de TI");
        Cargo coordenador = cargo(2L, "Coordenador de TI");
        when(repository.findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc())
                .thenReturn(List.of(diretor, coordenador), List.of(diretor, coordenador));

        List<Cargo> resultado = service.atualizarHierarquia(new CargoHierarquiaRequestDTO(List.of(
                new CargoHierarquiaItemRequestDTO(1L, null, 0),
                new CargoHierarquiaItemRequestDTO(2L, 1L, 0)
        )));

        assertEquals(1L, coordenador.getCargoSuperiorId());
        assertEquals(List.of(diretor, coordenador), resultado);
        verify(repository).saveAll(List.of(diretor, coordenador));
    }

    @Test
    void deveRejeitarCicloNaHierarquia() {
        Cargo diretor = cargo(1L, "Diretor de TI");
        Cargo coordenador = cargo(2L, "Coordenador de TI");
        when(repository.findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc())
                .thenReturn(List.of(diretor, coordenador));

        assertThrows(ResponseStatusException.class, () -> service.atualizarHierarquia(new CargoHierarquiaRequestDTO(List.of(
                new CargoHierarquiaItemRequestDTO(1L, 2L, 0),
                new CargoHierarquiaItemRequestDTO(2L, 1L, 0)
        ))));
    }

    private Cargo cargo(String nome) {
        Cargo cargo = new Cargo();
        cargo.setNome(nome);
        return cargo;
    }

    private Cargo cargo(Long id, String nome) {
        Cargo cargo = cargo(nome);
        cargo.setId(id);
        cargo.setAtivo(true);
        return cargo;
    }
}
