package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusaoSolicitacoesMigrationTest {
    @Test
    void devePreservarPedidosExistentesEManterAuditoriaAoExcluirResponsavel() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:exclusao_solicitacoes;MODE=MySQL", "sa", "");
             var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO usuarios VALUES(3)");
            sql.execute("CREATE TABLE solicitacoes_acesso(id BIGINT PRIMARY KEY, origem VARCHAR(20), referencia_id BIGINT, status VARCHAR(20))");
            sql.execute("INSERT INTO solicitacoes_acesso VALUES(1,'USUARIO',7,'APROVADO'),(2,'GOOGLE',7,'PENDENTE')");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V67__exclusao_solicitacoes_acesso.sql"));
            try (var rs = sql.executeQuery("SELECT COUNT(*) FROM solicitacoes_acesso WHERE excluido_em IS NULL AND versao=0")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(2);
            }
            sql.execute("UPDATE solicitacoes_acesso SET excluido_em=CURRENT_TIMESTAMP,excluido_por=3 WHERE id=1");
            sql.execute("DELETE FROM usuarios WHERE id=3");
            try (var rs = sql.executeQuery("SELECT status,excluido_em,excluido_por FROM solicitacoes_acesso WHERE id=1")) {
                rs.next();
                assertThat(rs.getString("status")).isEqualTo("APROVADO");
                assertThat(rs.getTimestamp("excluido_em")).isNotNull();
                assertThat(rs.getObject("excluido_por")).isNull();
            }
            try (var rs = sql.executeQuery("SELECT origem,referencia_id FROM solicitacoes_acesso WHERE excluido_em IS NULL")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("origem")).isEqualTo("GOOGLE");
                assertThat(rs.getLong("referencia_id")).isEqualTo(7L);
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
