package com.climb.api.repository;

import com.climb.api.model.PessoaCliente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PessoaClienteRepository extends JpaRepository<PessoaCliente, Long> {
    @EntityGraph(attributePaths = "empresas")
    List<PessoaCliente> findAllByOrderByNomeAsc();

    @Override
    @EntityGraph(attributePaths = "empresas")
    Optional<PessoaCliente> findById(Long id);

    Optional<PessoaCliente> findByCpf(String cpf);
}
