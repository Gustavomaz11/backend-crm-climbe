package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.repository.CargoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CargoService {

    private final CargoRepository repository;

    public CargoService(CargoRepository repository) {
        this.repository = repository;
    }

    public List<Cargo> listar() {
        return repository.findAllByAtivoTrueOrderByNomeAsc();
    }

    public Cargo buscarPorId(Long id) {
        return repository.findById(id).orElseThrow();
    }

    public Cargo criar(Cargo cargo) {
        cargo.setAtivo(true);
        return repository.save(cargo);
    }

    public Cargo atualizar(Long id, Cargo atualizado) {
        Cargo cargo = buscarPorId(id);
        cargo.setNome(atualizado.getNome());
        return repository.save(cargo);
    }

    public void deletar(Long id) {
        Cargo cargo = buscarPorId(id);
        cargo.setAtivo(false);
        repository.save(cargo);
    }
}
