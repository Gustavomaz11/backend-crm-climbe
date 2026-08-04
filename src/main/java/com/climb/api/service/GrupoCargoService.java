package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.GrupoCargo;
import com.climb.api.model.dto.GrupoCargoRequestDTO;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.GrupoCargoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class GrupoCargoService {

    private final GrupoCargoRepository repository;
    private final CargoRepository cargoRepository;

    public GrupoCargoService(GrupoCargoRepository repository, CargoRepository cargoRepository) {
        this.repository = repository;
        this.cargoRepository = cargoRepository;
    }

    @Transactional(readOnly = true)
    public List<GrupoCargo> listar() {
        return repository.findAllByAtivoTrueOrderByNomeAsc();
    }

    @Transactional
    public GrupoCargo criar(GrupoCargoRequestDTO request) {
        String nome = normalizarNome(request.nome());
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe um grupo de cargos com este nome");
        }

        GrupoCargo grupo = new GrupoCargo();
        grupo.setNome(nome);
        grupo.setDescricao(normalizarDescricao(request.descricao()));
        return repository.save(grupo);
    }

    @Transactional
    public GrupoCargo atualizar(Long id, GrupoCargoRequestDTO request) {
        GrupoCargo grupo = buscarAtivo(id);
        String nome = normalizarNome(request.nome());
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe um grupo de cargos com este nome");
        }

        grupo.setNome(nome);
        grupo.setDescricao(normalizarDescricao(request.descricao()));
        return repository.save(grupo);
    }

    @Transactional
    public void desativar(Long id) {
        GrupoCargo grupo = buscarAtivo(id);
        List<Cargo> cargos = cargoRepository.findAllByGrupoIdAndAtivoTrue(id);
        cargos.forEach(cargo -> cargo.setGrupoId(null));
        cargoRepository.saveAll(cargos);
        grupo.setAtivo(false);
        repository.save(grupo);
    }

    @Transactional
    public Cargo vincularCargo(Long cargoId, Long grupoId) {
        Cargo cargo = cargoRepository.findByIdAndAtivoTrue(cargoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cargo ativo nao encontrado"));
        if (grupoId != null) {
            buscarAtivo(grupoId);
        }
        cargo.setGrupoId(grupoId);
        return cargoRepository.save(cargo);
    }

    private GrupoCargo buscarAtivo(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo de cargos nao encontrado"));
    }

    private String normalizarNome(String nome) {
        return nome.trim();
    }

    private String normalizarDescricao(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }
}
