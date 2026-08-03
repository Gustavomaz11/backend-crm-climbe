package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.Usuario;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CargoHierarquiaAcessoService {

    private final CargoRepository cargoRepository;
    private final UsuarioRepository usuarioRepository;

    public CargoHierarquiaAcessoService(
            CargoRepository cargoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.cargoRepository = cargoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Set<Long> buscarUsuariosVisiveis(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        Set<Long> usuariosVisiveis = new HashSet<>();
        usuariosVisiveis.add(usuarioId);
        if (usuario.getCargo() == null) {
            return usuariosVisiveis;
        }

        Set<Long> cargosSubordinados = buscarCargosDescendentes(usuario.getCargo().getId());
        if (!cargosSubordinados.isEmpty()) {
            usuariosVisiveis.addAll(usuarioRepository.findIdsBySituacaoAndCargoIds("ATIVO", cargosSubordinados));
        }
        return usuariosVisiveis;
    }

    private Set<Long> buscarCargosDescendentes(Long cargoId) {
        Map<Long, List<Long>> subordinadosPorSuperior = new HashMap<>();
        for (Cargo cargo : cargoRepository.findAllByAtivoTrueOrderByOrdemHierarquiaAscNomeAsc()) {
            if (cargo.getCargoSuperiorId() == null) continue;
            subordinadosPorSuperior
                    .computeIfAbsent(cargo.getCargoSuperiorId(), ignored -> new ArrayList<>())
                    .add(cargo.getId());
        }

        Set<Long> descendentes = new HashSet<>();
        ArrayDeque<Long> pendentes = new ArrayDeque<>(subordinadosPorSuperior.getOrDefault(cargoId, List.of()));
        while (!pendentes.isEmpty()) {
            Long descendente = pendentes.removeFirst();
            if (!descendentes.add(descendente)) continue;
            pendentes.addAll(subordinadosPorSuperior.getOrDefault(descendente, List.of()));
        }
        return descendentes;
    }
}
