package com.climb.api.repository;

import com.climb.api.model.OAuthLoginRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface OAuthLoginRequestRepository extends JpaRepository<OAuthLoginRequest, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OAuthLoginRequest r where r.stateHash = :hash")
    Optional<OAuthLoginRequest> buscarParaConsumir(@Param("hash") String hash);
    void deleteByExpiraEmBefore(Instant agora);
}
