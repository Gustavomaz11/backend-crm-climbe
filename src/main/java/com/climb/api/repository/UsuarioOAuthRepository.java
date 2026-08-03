package com.climb.api.repository;

import com.climb.api.model.OAuthProvider;
import com.climb.api.model.UsuarioOAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioOAuthRepository extends JpaRepository<UsuarioOAuth, Long> {

    Optional<UsuarioOAuth> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    Optional<UsuarioOAuth> findByUsuarioIdAndProvider(Long usuarioId, OAuthProvider provider);

    @Query("""
            select oauth.usuario.id as usuarioId, oauth.avatarUrl as avatarUrl
            from UsuarioOAuth oauth
            where oauth.provider = :provider
              and oauth.usuario.id in :usuarioIds
            """)
    List<UsuarioOAuthAvatarProjection> findAvataresByUsuarioIdsAndProvider(
            @Param("usuarioIds") Collection<Long> usuarioIds,
            @Param("provider") OAuthProvider provider);

    boolean existsByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
