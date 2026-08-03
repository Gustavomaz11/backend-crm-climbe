package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.dto.CargoHierarquiaItemRequestDTO;
import com.climb.api.model.dto.CargoHierarquiaRequestDTO;
import com.climb.api.repository.CargoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        cargo.setCargoSuperiorId(null);
        int proximaOrdem = repository
                .findByCargoSuperiorIdAndAtivoTrueOrderByOrdemHierarquiaAscNomeAsc(null)
                .stream()
                .mapToInt(Cargo::getOrdemHierarquia)
                .max()
                .orElse(-1) + 1;
        cargo.setOrdemHierarquia(proximaOrdem);
        return repository.save(cargo);
    }

    public Cargo atualizar(Long id, Cargo atualizado) {
        Cargo cargo = buscarPorId(id);
        cargo.setNome(atualizado.getNome());
        return repository.save(cargo);
    }

    @Transactional
    public void deletar(Long id) {
        Cargo cargo = buscarPorId(id);
        List<Cargo> subordinados = repository
                .findByCargoSuperiorIdAndAtivoTrueOrderByOrdemHierarquiaAscNomeAsc(id);
        subordinados.forEach(subordinado -> subordinado.setCargoSuperiorId(cargo.getCargoSuperiorId()));
        cargo.setAtivo(false);
        repository.saveAll(subordinados);
        repository.save(cargo);
    }

    @Transactional
    public List<Cargo> atualizarHierarquia(CargoHierarquiaRequestDTO request) {
        List<Cargo> cargosAtivos = repository.findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc();
        Map<Long, Cargo> cargosPorId = cargosAtivos.stream()
                .collect(Collectors.toMap(Cargo::getId, Function.identity()));
        Map<Long, CargoHierarquiaItemRequestDTO> itensPorCargo = validarHierarquia(request, cargosPorId.keySet());

        itensPorCargo.forEach((cargoId, item) -> {
            Cargo cargo = cargosPorId.get(cargoId);
            cargo.setCargoSuperiorId(item.cargoSuperiorId());
            cargo.setOrdemHierarquia(Math.max(0, item.ordem() == null ? 0 : item.ordem()));
        });

        repository.saveAll(cargosAtivos);
        return repository.findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc();
    }

    private Map<Long, CargoHierarquiaItemRequestDTO> validarHierarquia(
            CargoHierarquiaRequestDTO request,
            Set<Long> cargosAtivos
    ) {
        if (request == null || request.cargos() == null) {
            throw hierarquiaInvalida("A hierarquia de cargos é obrigatória");
        }

        Map<Long, CargoHierarquiaItemRequestDTO> itensPorCargo = new HashMap<>();
        for (CargoHierarquiaItemRequestDTO item : request.cargos()) {
            if (item == null || item.cargoId() == null || itensPorCargo.put(item.cargoId(), item) != null) {
                throw hierarquiaInvalida("A hierarquia contém cargos inválidos ou duplicados");
            }
        }
        if (!itensPorCargo.keySet().equals(cargosAtivos)) {
            throw hierarquiaInvalida("Todos os cargos ativos devem fazer parte da hierarquia");
        }

        itensPorCargo.forEach((cargoId, item) -> {
            Long superiorId = item.cargoSuperiorId();
            if (superiorId != null && (!cargosAtivos.contains(superiorId) || superiorId.equals(cargoId))) {
                throw hierarquiaInvalida("O superior direto informado é inválido");
            }
        });
        validarAusenciaDeCiclos(itensPorCargo);
        return itensPorCargo;
    }

    private void validarAusenciaDeCiclos(Map<Long, CargoHierarquiaItemRequestDTO> itensPorCargo) {
        for (Long cargoId : itensPorCargo.keySet()) {
            Set<Long> caminho = new HashSet<>();
            Long atual = cargoId;
            while (atual != null) {
                if (!caminho.add(atual)) {
                    throw hierarquiaInvalida("A hierarquia não pode conter ciclos");
                }
                CargoHierarquiaItemRequestDTO item = itensPorCargo.get(atual);
                atual = item == null ? null : item.cargoSuperiorId();
            }
        }
    }

    private ResponseStatusException hierarquiaInvalida(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
