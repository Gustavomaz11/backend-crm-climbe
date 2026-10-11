package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PermissoesCrudMigrationTest {
    @Test
    void preservaAcessosIndividuaisGruposEAprovacoesSemDuplicarOuConcederAOutros() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:permissoes_crud;MODE=MySQL", "sa", "");
             var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE permissoes(id_permissao BIGINT AUTO_INCREMENT PRIMARY KEY, codigo VARCHAR(100) UNIQUE, descricao VARCHAR(255))");
            sql.execute("CREATE TABLE usuario_permissoes(id BIGINT AUTO_INCREMENT PRIMARY KEY, id_usuario BIGINT, id_permissao BIGINT REFERENCES permissoes(id_permissao))");
            sql.execute("CREATE TABLE grupo_permissao_itens(grupo_id BIGINT, permissao_id BIGINT REFERENCES permissoes(id_permissao), PRIMARY KEY(grupo_id, permissao_id))");
            sql.execute("CREATE TABLE oauth2_pending_permissoes(pending_id BIGINT, id_permissao BIGINT REFERENCES permissoes(id_permissao), PRIMARY KEY(pending_id, id_permissao))");
            int id = 0;
            var recursos = List.of("CONTRATO", "CARGO", "DOCUMENTO_JURIDICO", "RELATORIO", "PROPOSTA");
            for (var recurso : recursos) {
                id++;
                sql.execute("INSERT INTO permissoes VALUES(" + id + ",'" + recurso + "_CRUD','Acesso completo')");
                sql.execute("INSERT INTO usuario_permissoes(id_usuario,id_permissao) VALUES(1," + id + ")");
                sql.execute("INSERT INTO grupo_permissao_itens VALUES(10," + id + ")");
                sql.execute("INSERT INTO oauth2_pending_permissoes VALUES(20," + id + ")");
            }
            sql.execute("INSERT INTO permissoes VALUES(6,'CONTRATO_LER','Ler contratos'),(7,'ARQUIVO_UPLOAD','Upload')");
            sql.execute("INSERT INTO usuario_permissoes(id_usuario,id_permissao) VALUES(1,6),(2,6),(3,7)");
            sql.execute("INSERT INTO grupo_permissao_itens VALUES(10,6)");
            sql.execute("INSERT INTO oauth2_pending_permissoes VALUES(20,6)");

            var script = new ClassPathResource("db/migration/V66__separar_permissoes_crud.sql");
            ScriptUtils.executeSqlScript(conn, script);
            // A segunda execução não duplica vínculos já convertidos.
            ScriptUtils.executeSqlScript(conn, script);
            for (var query : List.of(
                    "SELECT p.codigo FROM usuario_permissoes u JOIN permissoes p ON p.id_permissao=u.id_permissao WHERE u.id_usuario=1",
                    "SELECT p.codigo FROM grupo_permissao_itens g JOIN permissoes p ON p.id_permissao=g.permissao_id WHERE g.grupo_id=10",
                    "SELECT p.codigo FROM oauth2_pending_permissoes o JOIN permissoes p ON p.id_permissao=o.id_permissao WHERE o.pending_id=20")) {
                var codigos = new java.util.ArrayList<String>();
                try (var rs = sql.executeQuery(query)) { while (rs.next()) codigos.add(rs.getString(1)); }
                assertThat(codigos).hasSize(20).doesNotHaveDuplicates();
                for (var recurso : recursos) {
                    assertThat(codigos).contains(recurso + "_LER", recurso + "_CRIAR", recurso + "_EDITAR", recurso + "_DELETAR");
                }
            }
            try (var rs = sql.executeQuery("SELECT p.codigo FROM usuario_permissoes u JOIN permissoes p ON p.id_permissao=u.id_permissao WHERE u.id_usuario=2")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("CONTRATO_LER"); assertThat(rs.next()).isFalse();
            }
            try (var rs = sql.executeQuery("SELECT codigo FROM permissoes WHERE codigo LIKE '%CRUD'")) { assertThat(rs.next()).isFalse(); }
            try (var rs = sql.executeQuery("SELECT COUNT(*) FROM usuario_permissoes WHERE id_usuario=3 AND id_permissao=7")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
