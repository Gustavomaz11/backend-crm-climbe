package com.climb.api.service;

import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OAuth2PendingService {

    private static final int PENDING_TTL_DAYS = 30;

    private final OAuth2PendingRegistrationRepository repository;

    public OAuth2PendingService(OAuth2PendingRegistrationRepository repository) {
        this.repository = repository;
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

        return repository.save(pending);
    }

    @Transactional
    public void aprovar(Long pendingId, Long aprovadorUsuarioId) {
        OAuth2PendingRegistration pending = repository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Cadastro pendente nao encontrado"));

        if (Boolean.TRUE.equals(pending.getConsumido())) {
            throw new RuntimeException("Cadastro pendente ja foi concluido");
        }
        if (pending.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cadastro pendente expirado");
        }
        if (Boolean.TRUE.equals(pending.getAprovado())) {
            return;
        }

        pending.setAprovado(true);
        pending.setAprovadoEm(LocalDateTime.now());
        pending.setAprovadoPor(aprovadorUsuarioId);
        repository.save(pending);
    }

    @Transactional
    public void recusar(Long pendingId) {
        OAuth2PendingRegistration pending = repository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Cadastro pendente nao encontrado"));

        if (Boolean.TRUE.equals(pending.getConsumido())) {
            throw new RuntimeException("Cadastro pendente ja foi concluido");
        }

        pending.setConsumido(true);
        pending.setAprovado(false);
        repository.save(pending);
    }

    public Optional<OAuth2PendingRegistration> findAtivoPorProvider(OAuthProvider provider, String providerUserId) {
        return repository.findByProviderAndProviderUserIdAndConsumidoFalse(provider, providerUserId);
    }

    public List<OAuth2PendingRegistration> listarPendentes() {
        return repository.findByConsumidoFalseAndAprovadoFalseAndExpiraEmAfter(LocalDateTime.now());
    }
}
