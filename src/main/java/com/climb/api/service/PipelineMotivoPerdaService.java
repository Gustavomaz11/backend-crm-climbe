package com.climb.api.service;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineMotivoPerda;
import com.climb.api.model.dto.PipelineMotivoPerdaRequestDTO;
import com.climb.api.model.dto.PipelineMotivoPerdaResponseDTO;
import com.climb.api.repository.PipelineMotivoPerdaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PipelineMotivoPerdaService {
    private final PipelineMotivoPerdaRepository repository;
    private final RbacService rbacService;

    public PipelineMotivoPerdaService(PipelineMotivoPerdaRepository repository, RbacService rbacService) {
        this.repository = repository;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public List<PipelineMotivoPerdaResponseDTO> listarAtivos(Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL);
        return repository.findByAtivoTrueOrderByPosicaoAscNomeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PipelineMotivoPerdaResponseDTO> listarTodos(Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_MOTIVO_PERDA_VISUALIZAR);
        return repository.findAllByOrderByPosicaoAscNomeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PipelineMotivoPerdaResponseDTO criar(Long usuarioId, PipelineMotivoPerdaRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_MOTIVO_PERDA_CRIAR);
        String nome = dto.nome().trim();
        if (repository.existsByNomeIgnoreCase(nome)) conflito();
        PipelineMotivoPerda motivo = new PipelineMotivoPerda();
        motivo.setNome(nome);
        motivo.setDescricao(normalizar(dto.descricao()));
        motivo.setAtivo(dto.ativo());
        motivo.setPosicao(repository.findAllByOrderByPosicaoAscNomeAsc().stream()
                .map(PipelineMotivoPerda::getPosicao).max(Integer::compareTo).orElse(0) + 1);
        return toResponse(repository.save(motivo));
    }

    @Transactional
    public PipelineMotivoPerdaResponseDTO atualizar(Long id, Long usuarioId, PipelineMotivoPerdaRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_MOTIVO_PERDA_EDITAR);
        PipelineMotivoPerda motivo = buscar(id);
        String nome = dto.nome().trim();
        if (repository.existsByNomeIgnoreCaseAndIdMotivoNot(nome, id)) conflito();
        motivo.setNome(nome);
        motivo.setDescricao(normalizar(dto.descricao()));
        motivo.setAtivo(dto.ativo());
        return toResponse(repository.save(motivo));
    }

    @Transactional(readOnly = true)
    public PipelineMotivoPerda buscarAtivoObrigatorio(Long id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O motivo da perda é obrigatório");
        return repository.findById(id)
                .filter(motivo -> Boolean.TRUE.equals(motivo.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motivo de perda ativo não encontrado"));
    }

    private PipelineMotivoPerda buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motivo de perda não encontrado"));
    }

    private void conflito() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um motivo de perda com este nome");
    }

    private void exigirPermissao(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para gerenciar motivos de perda");
        }
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private PipelineMotivoPerdaResponseDTO toResponse(PipelineMotivoPerda motivo) {
        return new PipelineMotivoPerdaResponseDTO(
                motivo.getIdMotivo(), motivo.getNome(), motivo.getDescricao(), motivo.getPosicao(),
                Boolean.TRUE.equals(motivo.getAtivo()), motivo.getCriadoEm(), motivo.getAtualizadoEm());
    }
}
