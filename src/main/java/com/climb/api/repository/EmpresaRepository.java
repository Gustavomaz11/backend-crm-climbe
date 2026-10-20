package com.climb.api.repository;

import com.climb.api.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Empresa e where e.idEmpresa = :id")
    Optional<Empresa> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Empresa> findByCnpj(String cnpj);
    Optional<Empresa> findFirstByCnpjIn(Collection<String> cnpjs);

}
