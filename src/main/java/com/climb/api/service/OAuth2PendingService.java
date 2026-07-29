package com.climb.api.service;

import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import com.climb.api.model.SolicitacaoAcessoStatus;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OAuth2PendingService {

    private static final int PENDING_TTL_DAYS = 30;

    private final OAuth2PendingRegistrationRepository repository;
    private final AprovacaoAcessoService aprovacaoAcessoService;
    private final SolicitacaoAcessoService solicitacaoAcessoService;

    public OAuth2PendingService(OAuth2PendingRegistrationRepository repository,
                                AprovacaoAcessoService aprovacaoAcessoService,
                                SolicitacaoAcessoService solicitacaoAcessoService) {
        this.repository = repository;
        this.aprovacaoAcessoService = aprovacaoAcessoService;
        this.solicitacaoAcessoService = solicitacaoAcessoService;
    }

    @Transactional
    public OAuth2PendingRegistration criarPendingGoogle(String providerUserId,
                                                       String email,
                                                       String nome,
                                                       String avatarUrl) {
        Optional<OAuth2PendingRegistration> existente = repository
                .findByProviderAndProviderUserIdAndConsumidoFalse(OAuthProvider.GOOGLE, providerUserId);
        if (existente.isPresent()) {
            return existente.get();
        }

        LocalDateTime now = LocalDateTime.now();
        OAuth2PendingRegistration pending = new OAuth2PendingRegistration();
        pending.setProvider(OAuthProvider.GOOGLE);
        pending.setProviderUserId(providerUserId);
        pending.setEmail(email);
        pending.setNome(nome);
        pending.setAvatarUrl(avatarUrl);
        pending.setTokenUnico(UUID.randomUUID().toString());
        pending.setExpiraEm(now.plusDays(PENDING_TTL_DAYS));
        pending.setConsumido(false);
        pending.setAprovado(false);
        pending.setCriadoEm(now);

        OAuth2PendingRegistration salvo = repository.save(pending);
        solicitacaoAcessoService.registrarGoogle(salvo);
        return salvo;
    }

    @Transactional
    public void aprovar(Long pendingId,
                        Long aprovadorUsuarioId,
                        Long cargoId,
                        Set<Long> permissaoIds) {
        OAuth2PendingRegistration pending = repository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Cadastro pendente nao encontrado"));

        if (Boolean.TRUE.equals(pending.getConsumido())) {
            throw new RuntimeException("Cadastro pendente ja foi concluido");
        }
        if (pending.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cadastro pendente expirado");
        }

        AprovacaoAcessoService.AtribuicaoAcesso atribuicao =
                aprovacaoAcessoService.resolver(cargoId, permissaoIds);

        pending.setAprovado(true);
        pending.setAprovadoEm(LocalDateTime.now());
        pending.setAprovadoPor(aprovadorUsuarioId);
        pending.setCargo(atribuicao.cargo());
        pending.getPermissoes().clear();
        pending.getPermissoes().addAll(atribuicao.permissoes());
        repository.save(pending);
        solicitacaoAcessoService.decidir(
                SolicitacaoAcessoOrigem.GOOGLE,
                pendingId,
                SolicitacaoAcessoStatus.APROVADO,
                aprovadorUsuarioId,
                atribuicao.cargo().getNome());
    }

    @Transactional
    public void recusar(Long pendingId, Long aprovadorUsuarioId) {
        OAuth2PendingRegistration pending = repository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Cadastro pendente nao encontrado"));

        if (Boolean.TRUE.equals(pending.getConsumido())) {
            throw new RuntimeException("Cadastro pendente ja foi concluido");
        }

        pending.setConsumido(true);
        pending.setAprovado(false);
        repository.save(pending);
        solicitacaoAcessoService.decidir(
                SolicitacaoAcessoOrigem.GOOGLE,
                pendingId,
                SolicitacaoAcessoStatus.RECUSADO,
                aprovadorUsuarioId,
                null);
    }

    public Optional<OAuth2PendingRegistration> findAtivoPorProvider(OAuthProvider provider, String providerUserId) {
        return repository.findByProviderAndProviderUserIdAndConsumidoFalse(provider, providerUserId);
    }

    public List<OAuth2PendingRegistration> listarPendentes() {
        return repository.findByConsumidoFalseAndAprovadoFalseAndExpiraEmAfter(LocalDateTime.now());
    }
}
