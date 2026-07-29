package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.repository.CargoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoServiceTest {

    @Mock
    private CargoRepository repository;

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

    private Cargo cargo(String nome) {
        Cargo cargo = new Cargo();
        cargo.setNome(nome);
        return cargo;
    }
}
