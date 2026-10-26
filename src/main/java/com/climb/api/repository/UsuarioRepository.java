package com.climb.api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.climb.api.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    // Grupos são modelos aplicados como permissões individuais; não há vínculo usuário/grupo.
    @Query(value = """
            SELECT usuario.* FROM usuarios usuario
            WHERE usuario.situacao = 'ATIVO'
              AND EXISTS (
                SELECT 1 FROM grupos_permissoes grupo
                WHERE grupo.ativo = true AND LOWER(TRIM(grupo.nome)) = 'administrador'
                  AND EXISTS (SELECT 1 FROM grupo_permissao_itens item WHERE item.grupo_id = grupo.id)
                  AND NOT EXISTS (
                    SELECT 1 FROM grupo_permissao_itens item
                    WHERE item.grupo_id = grupo.id
                      AND NOT EXISTS (
                        SELECT 1 FROM usuario_permissoes acesso
                        WHERE acesso.id_usuario = usuario.id AND acesso.id_permissao = item.permissao_id
                      )
                  )
              )
            ORDER BY usuario.id
            """, nativeQuery = true)
    List<Usuario> buscarAdministradoresAtivos();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select usuario from Usuario usuario where usuario.id = :id")
    Optional<Usuario> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select usuario from Usuario usuario join fetch usuario.cargo cargo
            where usuario.situacao = 'ATIVO' and cargo.ativo = true
              and lower(trim(cargo.nome)) = 'diretor comercial'
            order by usuario.id
            """)
    List<Usuario> buscarDiretoresComerciaisAtivos();

    @Query("""
            select usuario from Usuario usuario join fetch usuario.cargo cargo
            where usuario.situacao = 'ATIVO' and cargo.ativo = true
              and (lower(cargo.nome) like '%jurídic%' or lower(cargo.nome) like '%juridic%')
            order by usuario.id
            """)
    List<Usuario> buscarJuridicoAtivo();

    Optional<Usuario> findByCpf(String cpf);

    boolean existsByIdAndSituacao(Long id, String situacao);

    @Query("""
            select coalesce(permissao.codigo, '')
            from Usuario usuario
            left join usuario.permissoes permissao
            where usuario.id = :usuarioId
            """)
    List<String> findCodigosPermissoesById(@Param("usuarioId") Long usuarioId);

    List<Usuario> findAllBySituacaoOrderByNomeCompletoAsc(String situacao);

    List<Usuario> findAllBySituacaoInOrderByNomeCompletoAsc(Collection<String> situacoes);

    List<Usuario> findAllBySituacaoAndCargo_IdOrderByNomeCompletoAsc(String situacao, Long cargoId);

    @Query("""
            select usuario.id
            from Usuario usuario
            where usuario.situacao = :situacao
              and usuario.cargo.id in :cargoIds
            """)
    Set<Long> findIdsBySituacaoAndCargoIds(
            @Param("situacao") String situacao,
            @Param("cargoIds") Collection<Long> cargoIds
    );

}
