package com.climb.api.repository;

import com.climb.api.model.EmpresaDrive;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EmpresaDriveRepository extends JpaRepository<EmpresaDrive, Long> {
    List<EmpresaDrive> findTop10ByPendenteTrueAndTentarEmLessThanEqualOrderByTentarEmAsc(LocalDateTime agora);
}
