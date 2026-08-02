package com.climb.api.service;

import com.climb.api.model.GrupoPermissao;
import com.climb.api.model.Permissao;
import com.climb.api.model.dto.GrupoPermissaoRequestDTO;
import com.climb.api.model.dto.GrupoPermissaoResponseDTO;
import com.climb.api.repository.GrupoPermissaoRepository;
import com.climb.api.repository.PermissaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GrupoPermissaoService {

    private final GrupoPermissaoRepository repository;
    private final PermissaoRepository permissaoRepository;

    public GrupoPermissaoService(GrupoPermissaoRepository repository,
                                 PermissaoRepository permissaoRepository) {
        this.repository = repository;
        this.permissaoRepository = permissaoRepository;
    }

    @Transactional(readOnly = true)
    public List<GrupoPermissaoResponseDTO> listar() {
        return repository.findAllByAtivoTrueOrderByNomeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public GrupoPermissaoResponseDTO criar(GrupoPermissaoRequestDTO dto) {
        String nome = dto.nome().trim();
        if (repository.existsByNomeIgnoreCaseAndAtivoTrue(nome)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe um grupo com este nome");
        }
        GrupoPermissao grupo = new GrupoPermissao();
        grupo.setNome(nome);
        grupo.setDescricao(normalizarDescricao(dto.descricao()));
        grupo.setPermissoes(resolverPermissoes(dto.permissaoIds()));
        return toResponse(repository.save(grupo));
    }

    @Transactional
    public GrupoPermissaoResponseDTO atualizar(Long id, GrupoPermissaoRequestDTO dto) {
        GrupoPermissao grupo = buscar(id);
        grupo.setNome(dto.nome().trim());
        grupo.setDescricao(normalizarDescricao(dto.descricao()));
        grupo.setPermissoes(resolverPermissoes(dto.permissaoIds()));
        return toResponse(repository.save(grupo));
    }

    @Transactional
    public void desativar(Long id) {
        GrupoPermissao grupo = buscar(id);
        grupo.setAtivo(false);
        repository.save(grupo);
    }

    private GrupoPermissao buscar(Long id) {
        return repository.findById(id)
                .filter(GrupoPermissao::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo de permissoes nao encontrado"));
    }

    private Set<Permissao> resolverPermissoes(Set<Long> ids) {
        Set<Long> solicitadas = ids == null ? Set.of() : ids;
        List<Permissao> encontradas = permissaoRepository.findAllById(solicitadas);
        if (encontradas.size() != solicitadas.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uma ou mais permissoes nao existem");
        }
        return new LinkedHashSet<>(encontradas);
    }

    private GrupoPermissaoResponseDTO toResponse(GrupoPermissao grupo) {
        return new GrupoPermissaoResponseDTO(
                grupo.getId(), grupo.getNome(), grupo.getDescricao(), Set.copyOf(grupo.getPermissoes()));
    }

    private String normalizarDescricao(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }
}
