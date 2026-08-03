package com.climb.api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.climb.api.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

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

}
