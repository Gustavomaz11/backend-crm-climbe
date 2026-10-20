package com.climb.api.repository;

import com.climb.api.model.EmpresaDriveServico;
import com.climb.api.model.enums.ServicoComercial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmpresaDriveServicoRepository extends JpaRepository<EmpresaDriveServico, Long> {
    List<EmpresaDriveServico> findByEmpresaIdOrderByIdAsc(Long empresaId);
    Optional<EmpresaDriveServico> findByEmpresaIdAndServico(Long empresaId, ServicoComercial servico);
}
