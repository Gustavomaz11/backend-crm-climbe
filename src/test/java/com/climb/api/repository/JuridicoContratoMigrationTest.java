package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.HashSet;
import com.climb.api.model.PermissaoCodigo;
import static org.assertj.core.api.Assertions.assertThat;

class JuridicoContratoMigrationTest {
    @Test
    void adicionaQuinzePermissoesIndependentesEPreservaOCatalogoExistente() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:juridico;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE permissoes(id_permissao BIGINT AUTO_INCREMENT PRIMARY KEY,codigo VARCHAR(100) UNIQUE,descricao VARCHAR(255))");
            sql.execute("INSERT INTO permissoes(codigo,descricao) VALUES('CONTRATO_EDITAR','Editar contratos')");
            var migration = new ClassPathResource("db/migration/V69__permissoes_juridico_contratos.sql");
            ScriptUtils.executeSqlScript(conn, migration);
            ScriptUtils.executeSqlScript(conn, migration);
            var codes = new HashSet<String>();
            try (var rows = sql.executeQuery("SELECT codigo FROM permissoes")) {
                while (rows.next()) codes.add(rows.getString(1));
            }
            assertThat(codes).hasSize(16).contains("CONTRATO_EDITAR");
            for (var codigo : PermissaoCodigo.values()) {
                if (codigo.name().startsWith("JURIDICO_CONTRATO_")) assertThat(codes).contains(codigo.name());
            }
        }
    }
}
