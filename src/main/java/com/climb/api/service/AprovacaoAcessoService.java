package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.Permissao;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.PermissaoRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AprovacaoAcessoService {

    private final CargoRepository cargoRepository;
    private final PermissaoRepository permissaoRepository;

    public AprovacaoAcessoService(CargoRepository cargoRepository,
                                  PermissaoRepository permissaoRepository) {
        this.cargoRepository = cargoRepository;
        this.permissaoRepository = permissaoRepository;
    }

    public AtribuicaoAcesso resolver(Long cargoId, Set<Long> permissaoIds) {
        if (cargoId == null) {
            throw new IllegalArgumentException("Cargo e obrigatorio");
        }

        Set<Long> idsUnicos = permissaoIds == null
                ? Set.of()
                : new LinkedHashSet<>(permissaoIds);
        idsUnicos.removeIf(id -> id == null || id <= 0);

        if (idsUnicos.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos uma permissao");
        }

        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new IllegalArgumentException("Cargo nao encontrado"));
        List<Permissao> permissoesEncontradas = permissaoRepository.findAllById(idsUnicos);

        if (permissoesEncontradas.size() != idsUnicos.size()) {
            throw new IllegalArgumentException("Uma ou mais permissoes nao foram encontradas");
        }

        return new AtribuicaoAcesso(cargo, new LinkedHashSet<>(permissoesEncontradas));
    }

    public record AtribuicaoAcesso(Cargo cargo, Set<Permissao> permissoes) {
    }
}
