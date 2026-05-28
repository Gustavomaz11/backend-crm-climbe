package com.climb.api.repository;

import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OAuth2PendingRegistrationRepository extends JpaRepository<OAuth2PendingRegistration, Long> {

    Optional<OAuth2PendingRegistration> findByProviderAndProviderUserIdAndConsumidoFalse(
            OAuthProvider provider, String providerUserId);

    List<OAuth2PendingRegistration> findByConsumidoFalseAndAprovadoFalseAndExpiraEmAfter(LocalDateTime now);

    @Modifying
    @Query("UPDATE OAuth2PendingRegistration p SET p.consumido = true " +
           "WHERE p.id = :id AND p.consumido = false")
    int consumirSeNaoConsumido(@Param("id") Long id);

    void deleteByExpiraEmBefore(LocalDateTime dataHora);
}
