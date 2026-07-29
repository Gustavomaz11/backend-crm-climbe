package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineComentarioRequestDTO;
import com.climb.api.model.dto.PipelineComentarioResponseDTO;
import com.climb.api.model.enums.PipelineHistoricoTipo;
import com.climb.api.repository.PipelineVendasComentarioRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PipelineComentarioService {
    private final PipelineVendasComentarioRepository repository;
    private final PipelineVendasNegocioRepository negocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PipelineHistoricoService historicoService;
    private final RbacService rbacService;

    public PipelineComentarioService(PipelineVendasComentarioRepository repository,
                                     PipelineVendasNegocioRepository negocioRepository,
                                     UsuarioRepository usuarioRepository,
                                     PipelineHistoricoService historicoService,
                                     RbacService rbacService) {
        this.repository = repository;
        this.negocioRepository = negocioRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoService = historicoService;
        this.rbacService = rbacService;
    }

    @Transactional(readOnly = true)
    public List<PipelineComentarioResponseDTO> listar(Long negocioId, Long usuarioId) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_COMENTARIO_VISUALIZAR);
        buscarNegocio(negocioId);
        return repository.findByNegocioIdNegocioOrderByCriadoEmDesc(negocioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PipelineComentarioResponseDTO criar(Long negocioId,
                                               Long usuarioId,
                                               PipelineComentarioRequestDTO dto) {
        exigirPermissao(usuarioId, PermissaoCodigo.COMERCIAL_COMENTARIO_CRIAR);
        PipelineVendasNegocio negocio = buscarNegocio(negocioId);
        Usuario autor = buscarUsuario(usuarioId);
        PipelineVendasComentario comentario = new PipelineVendasComentario();
        comentario.setNegocio(negocio);
        comentario.setAutor(autor);
        comentario.setConteudo(dto.conteudo().trim());
        PipelineVendasComentario salvo = repository.save(comentario);
        historicoService.registrar(negocio, usuarioId, PipelineHistoricoTipo.COMENTARIO_ADICIONADO,
                "Comentário incluído por " + autor.getNomeCompleto());
        return toResponse(salvo);
    }

    private PipelineVendasNegocio buscarNegocio(Long negocioId) {
        return negocioRepository.findById(negocioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negócio não encontrado"));
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private void exigirPermissao(Long usuarioId, PermissaoCodigo permissao) {
        if (usuarioId == null || !rbacService.temPermissao(usuarioId, permissao)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para comentários comerciais");
        }
    }

    private PipelineComentarioResponseDTO toResponse(PipelineVendasComentario comentario) {
        return new PipelineComentarioResponseDTO(
                comentario.getIdComentario(),
                comentario.getAutor().getId(),
                comentario.getAutor().getNomeCompleto(),
                comentario.getConteudo(),
                comentario.getCriadoEm()
        );
    }
}
