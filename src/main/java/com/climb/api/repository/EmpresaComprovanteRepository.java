package com.climb.api.repository;

import com.climb.api.model.EmpresaComprovante;
import com.climb.api.model.dto.EmpresaComprovanteDTO;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

public interface EmpresaComprovanteRepository extends JpaRepository<EmpresaComprovante, Long> {
    Optional<EmpresaComprovante> findByEmpresaIdAndTipo(Long empresaId, String tipo);
    Optional<EmpresaComprovante> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<EmpresaComprovante> findFirstByStatusOrderByIdAsc(String status);

    @Query("select new com.climb.api.model.dto.EmpresaComprovanteDTO(c.id,c.tipo,c.status,c.mensagem,c.geradoEm) from EmpresaComprovante c where c.empresaId=:empresaId order by c.id")
    List<EmpresaComprovanteDTO> listar(@Param("empresaId") Long empresaId);

    @Transactional @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmpresaComprovante c set c.status='PROCESSANDO', c.atualizadoEm=:agora, c.versao=c.versao+1 where c.id=:id and c.status='PENDENTE'")
    int assumir(@Param("id") Long id, @Param("agora") LocalDateTime agora);

    @Transactional @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmpresaComprovante c set c.status='ERRO', c.mensagem='Emissão interrompida. Tente novamente.', c.atualizadoEm=:agora, c.versao=c.versao+1 where c.status='PROCESSANDO' and c.atualizadoEm<:limite")
    int interromperAntigos(@Param("limite") LocalDateTime limite, @Param("agora") LocalDateTime agora);
}
