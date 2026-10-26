package com.climb.api.service;

import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.SolicitacaoAcesso;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import com.climb.api.model.SolicitacaoAcessoStatus;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.UsuarioPendenteResponseDTO;
import com.climb.api.repository.SolicitacaoAcessoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SolicitacaoAcessoService {

    private final SolicitacaoAcessoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final OAuth2PendingRegistrationRepository pendingRepository;
    private final AcessoNotificacaoService notificacoes;

    public SolicitacaoAcessoService(SolicitacaoAcessoRepository repository,
                                   UsuarioRepository usuarioRepository,
                                   OAuth2PendingRegistrationRepository pendingRepository,
                                   AcessoNotificacaoService notificacoes) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.pendingRepository = pendingRepository;
        this.notificacoes = notificacoes;
    }

    @Transactional
    public void registrarUsuario(Usuario usuario) {
        SolicitacaoAcesso solicitacao = novaSolicitacao(
                SolicitacaoAcessoOrigem.USUARIO,
                usuario.getId(),
                usuario.getNomeCompleto(),
                usuario.getEmail());
        solicitacao.setCpf(usuario.getCpf());
        solicitacao.setContato(usuario.getContato());
        solicitacao.setCargoNome(usuario.getCargo() != null ? usuario.getCargo().getNome() : null);
        repository.save(solicitacao);
        notificacoes.novaSolicitacao(solicitacao);
    }

    @Transactional
    public void registrarGoogle(OAuth2PendingRegistration pending) {
        SolicitacaoAcesso solicitacao = novaSolicitacao(
                SolicitacaoAcessoOrigem.GOOGLE,
                pending.getId(),
                pending.getNome() != null && !pending.getNome().isBlank() ? pending.getNome() : pending.getEmail(),
                pending.getEmail());
        solicitacao.setAvatarUrl(pending.getAvatarUrl());
        solicitacao.setCriadoEm(pending.getCriadoEm());
        solicitacao.setExpiraEm(pending.getExpiraEm());
        repository.save(solicitacao);
        notificacoes.novaSolicitacao(solicitacao);
    }

    @Transactional
    public void decidir(SolicitacaoAcessoOrigem origem,
                        Long referenciaId,
                        SolicitacaoAcessoStatus status,
                        Long decididoPor,
                        String cargoNome) {
        SolicitacaoAcesso solicitacao = repository.findByOrigemAndReferenciaId(origem, referenciaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Historico da solicitacao de acesso nao encontrado"));
        if (solicitacao.getExcluidoEm() != null || solicitacao.getStatus() != SolicitacaoAcessoStatus.PENDENTE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solicitacao de acesso ja foi decidida");
        }
        solicitacao.setStatus(status);
        solicitacao.setDecididoEm(LocalDateTime.now());
        solicitacao.setDecididoPor(decididoPor);
        if (cargoNome != null && !cargoNome.isBlank()) {
            solicitacao.setCargoNome(cargoNome);
        }
        salvarDecisao(solicitacao);
        if (status == SolicitacaoAcessoStatus.APROVADO) {
            notificacoes.acessoAprovado(solicitacao);
        }
    }

    @Transactional
    public void excluir(SolicitacaoAcessoOrigem origem, Long referenciaId, Long excluidoPor) {
        SolicitacaoAcesso solicitacao = repository.findByOrigemAndReferenciaId(origem, referenciaId)
                .filter(item -> item.getExcluidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Essa solicitação não foi encontrada ou já foi excluída."));
        LocalDateTime agora = LocalDateTime.now();
        if (solicitacao.getStatus() == SolicitacaoAcessoStatus.PENDENTE) {
            cancelarPedidoPendente(origem, referenciaId);
            solicitacao.setStatus(SolicitacaoAcessoStatus.RECUSADO);
            solicitacao.setDecididoEm(agora);
            solicitacao.setDecididoPor(excluidoPor);
        }
        solicitacao.setExcluidoEm(agora);
        solicitacao.setExcluidoPor(excluidoPor);
        salvarDecisao(solicitacao);
    }

    private void salvarDecisao(SolicitacaoAcesso solicitacao) {
        try {
            repository.saveAndFlush(solicitacao);
        } catch (OptimisticLockingFailureException exception) {
            throw pedidoAtualizado();
        }
    }

    private void cancelarPedidoPendente(SolicitacaoAcessoOrigem origem, Long referenciaId) {
        if (origem == SolicitacaoAcessoOrigem.USUARIO) {
            usuarioRepository.findById(referenciaId).ifPresent(usuario -> {
                if (!"ESPERANDO_APROVACAO".equals(usuario.getSituacao())) {
                    throw pedidoAtualizado();
                }
                usuario.setSituacao("INATIVO");
                usuarioRepository.save(usuario);
            });
        } else {
            pendingRepository.findById(referenciaId).ifPresent(pending -> {
                if (Boolean.TRUE.equals(pending.getAprovado())) {
                    throw pedidoAtualizado();
                }
                pending.setConsumido(true);
                pending.setAprovado(false);
                pendingRepository.save(pending);
            });
        }
    }

    private ResponseStatusException pedidoAtualizado() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Este pedido já foi atualizado. Recarregue a lista antes de excluí-lo.");
    }

    @Transactional(readOnly = true)
    public List<UsuarioPendenteResponseDTO> listar() {
        return repository.findByExcluidoEmIsNullOrderByCriadoEmDescIdDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private SolicitacaoAcesso novaSolicitacao(SolicitacaoAcessoOrigem origem,
                                              Long referenciaId,
                                              String nome,
                                              String email) {
        SolicitacaoAcesso solicitacao = new SolicitacaoAcesso();
        solicitacao.setOrigem(origem);
        solicitacao.setReferenciaId(referenciaId);
        solicitacao.setNomeCompleto(nome);
        solicitacao.setEmail(email);
        solicitacao.setStatus(SolicitacaoAcessoStatus.PENDENTE);
        solicitacao.setCriadoEm(LocalDateTime.now());
        return solicitacao;
    }

    private UsuarioPendenteResponseDTO toResponse(SolicitacaoAcesso solicitacao) {
        UsuarioPendenteResponseDTO dto = new UsuarioPendenteResponseDTO();
        dto.setOrigem(solicitacao.getOrigem().name());
        dto.setId(solicitacao.getReferenciaId());
        dto.setNomeCompleto(solicitacao.getNomeCompleto());
        dto.setEmail(solicitacao.getEmail());
        dto.setAvatarUrl(solicitacao.getAvatarUrl());
        dto.setCpf(solicitacao.getCpf());
        dto.setContato(solicitacao.getContato());
        dto.setCargoNome(solicitacao.getCargoNome());
        dto.setStatus(solicitacao.getStatus().name());
        dto.setCriadoEm(solicitacao.getCriadoEm());
        dto.setExpiraEm(solicitacao.getExpiraEm());
        dto.setDecididoEm(solicitacao.getDecididoEm());
        dto.setDecididoPor(solicitacao.getDecididoPor());
        return dto;
    }
}
