package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineHistoricoResponseDTO;
import com.climb.api.model.enums.PipelineHistoricoTipo;
import com.climb.api.repository.PipelineVendasHistoricoRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PipelineHistoricoService {
    private final PipelineVendasHistoricoRepository repository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RbacService rbacService;

    public PipelineHistoricoService(PipelineVendasHistoricoRepository repository,
                                    PipelineVendasNegocioRepository negocioRepository,
                                    UsuarioRepository usuarioRepository,
                                    RbacService rbacService) {
        this.repository = repository;
        this.negocioRepository = negocioRepository;
        this.usuarioRepository = usuarioRepository;
        this.rbacService = rbacService;
    }

    @Transactional
    public void registrar(PipelineVendasNegocio negocio,
                          Long usuarioId,
                          PipelineHistoricoTipo tipo,
                          String descricao) {
        PipelineVendasHistorico historico = new PipelineVendasHistorico();
        historico.setNegocio(negocio);
        historico.setUsuario(buscarUsuario(usuarioId));
        historico.setTipoEvento(tipo);
        historico.setDescricao(descricao);
        repository.save(historico);
    }

    @Transactional(readOnly = true)
    public List<PipelineHistoricoResponseDTO> listar(Long negocioId, Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_HISTORICO_VISUALIZAR);
        exigirNegocioExistente(negocioId);
        return repository.findByNegocioIdNegocioOrderByCriadoEmDesc(negocioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private void exigirNegocioExistente(Long negocioId) {
        if (!negocioRepository.existsById(negocioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Negócio não encontrado");
        }
    }

    private void exigirPermissao(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para consultar o histórico comercial");
        }
    }

    private PipelineHistoricoResponseDTO toResponse(PipelineVendasHistorico historico) {
        return new PipelineHistoricoResponseDTO(
                historico.getIdHistorico(),
                historico.getTipoEvento(),
                historico.getDescricao(),
                historico.getUsuario().getId(),
                historico.getUsuario().getNomeCompleto(),
                historico.getCriadoEm()
        );
    }
}
