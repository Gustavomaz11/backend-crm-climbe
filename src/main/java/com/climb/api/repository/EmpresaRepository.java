package com.climb.api.repository;

import com.climb.api.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findByCnpj(String cnpj);
    Optional<Empresa> findFirstByCnpjIn(Collection<String> cnpjs);

}
