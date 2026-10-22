package com.climb.api.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class IntegracaoMigrationTest {
    @Test void migraEstruturaEConcedeGestaoSomenteAAdministradoresExistentes() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:integracao_migration;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE empresas(id_empresa BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE permissoes(id_permissao BIGINT AUTO_INCREMENT PRIMARY KEY, codigo VARCHAR(100) UNIQUE, descricao VARCHAR(255))");
            sql.execute("CREATE TABLE usuario_permissoes(id BIGINT AUTO_INCREMENT PRIMARY KEY, id_usuario BIGINT, id_permissao BIGINT)");
            sql.execute("INSERT INTO usuarios VALUES(1),(2)");
            sql.execute("INSERT INTO permissoes(codigo,descricao) VALUES('PERMITIR_ACESSO','Administrador'),('CONTRATO_LER','Leitura')");
            sql.execute("INSERT INTO usuario_permissoes(id_usuario,id_permissao) VALUES(1,1),(2,2)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V82__tokens_integracoes.sql"));
            try (var rs = sql.executeQuery("SELECT up.id_usuario FROM usuario_permissoes up JOIN permissoes p ON p.id_permissao=up.id_permissao WHERE p.codigo='INTEGRACOES_GERENCIAR'")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getLong(1)).isEqualTo(1); assertThat(rs.next()).isFalse();
            }
            for (var table : java.util.List.of("INTEGRACAO_CLIENTES", "INTEGRACAO_CLIENTE_EMPRESAS", "INTEGRACAO_CLIENTE_OPERACOES", "INTEGRACAO_CLIENTE_METODOS", "INTEGRACAO_AUDITORIA")) {
                try (var rs = conn.getMetaData().getTables(null, null, table, null)) { assertThat(rs.next()).as(table).isTrue(); }
            }
        }
    }
}
