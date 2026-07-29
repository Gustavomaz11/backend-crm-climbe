package com.climb.api.service;

import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.SolicitacaoAcesso;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import com.climb.api.model.SolicitacaoAcessoStatus;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.UsuarioPendenteResponseDTO;
import com.climb.api.repository.SolicitacaoAcessoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SolicitacaoAcessoService {

    private final SolicitacaoAcessoRepository repository;

    public SolicitacaoAcessoService(SolicitacaoAcessoRepository repository) {
        this.repository = repository;
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
    }

    @Transactional
    public void decidir(SolicitacaoAcessoOrigem origem,
                        Long referenciaId,
                        SolicitacaoAcessoStatus status,
                        Long decididoPor,
                        String cargoNome) {
        SolicitacaoAcesso solicitacao = repository.findByOrigemAndReferenciaId(origem, referenciaId)
                .orElseThrow(() -> new IllegalStateException("Historico da solicitacao de acesso nao encontrado"));
        solicitacao.setStatus(status);
        solicitacao.setDecididoEm(LocalDateTime.now());
        solicitacao.setDecididoPor(decididoPor);
        if (cargoNome != null && !cargoNome.isBlank()) {
            solicitacao.setCargoNome(cargoNome);
        }
        repository.save(solicitacao);
    }

    @Transactional(readOnly = true)
    public List<UsuarioPendenteResponseDTO> listar() {
        return repository.findAllByOrderByCriadoEmDescIdDesc().stream()
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
