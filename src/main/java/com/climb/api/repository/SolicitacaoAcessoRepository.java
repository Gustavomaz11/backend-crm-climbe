package com.climb.api.repository;

import com.climb.api.model.SolicitacaoAcesso;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoAcessoRepository extends JpaRepository<SolicitacaoAcesso, Long> {
    Optional<SolicitacaoAcesso> findByOrigemAndReferenciaId(
            SolicitacaoAcessoOrigem origem,
            Long referenciaId);

    List<SolicitacaoAcesso> findAllByOrderByCriadoEmDescIdDesc();
}
