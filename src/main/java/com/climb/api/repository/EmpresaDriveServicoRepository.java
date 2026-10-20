package com.climb.api.repository;

import com.climb.api.model.EmpresaDriveServico;
import com.climb.api.model.enums.ServicoComercial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmpresaDriveServicoRepository extends JpaRepository<EmpresaDriveServico, Long> {
    List<EmpresaDriveServico> findTop10ByPastaIdIsNotNullAndPermissoesTentarEmLessThanEqualOrderByPermissoesTentarEmAsc(java.time.LocalDateTime agora);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select pasta from EmpresaDriveServico pasta where pasta.id = :id")
    Optional<EmpresaDriveServico> bloquear(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update EmpresaDriveServico pasta set pasta.permissoesTentarEm = :agora "
            + "where :empresaId is null or pasta.empresaId = :empresaId")
    int agendarPermissoes(@org.springframework.data.repository.query.Param("empresaId") Long empresaId,
            @org.springframework.data.repository.query.Param("agora") java.time.LocalDateTime agora);
    List<EmpresaDriveServico> findByEmpresaIdOrderByIdAsc(Long empresaId);
    Optional<EmpresaDriveServico> findByEmpresaIdAndServico(Long empresaId, ServicoComercial servico);
}
