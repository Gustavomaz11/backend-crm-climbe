package com.climb.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.climb.api.model.Proposta;
import com.climb.api.model.enums.PropostaStatus;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    List<Proposta> findByStatus(PropostaStatus status);

}