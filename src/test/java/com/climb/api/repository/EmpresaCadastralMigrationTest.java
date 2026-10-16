package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class EmpresaCadastralMigrationTest {
    @Test void preservaEmpresasExistentesEComprovantesPertencemApenasAEmpresa() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:cnpja_migration;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE empresas(id_empresa BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO empresas VALUES(1)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V74__empresa_dados_cadastrais_comprovantes.sql"));
            sql.execute("INSERT INTO empresa_comprovantes(empresa_id,cnpj,tipo,status,atualizado_em) VALUES(1,'37335118000180','RFB','PENDENTE',CURRENT_TIMESTAMP)");
            try (var result = sql.executeQuery("SELECT tipo_empresa,dados_cadastrais FROM empresas WHERE id_empresa=1")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isNull();
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO empresa_comprovantes(empresa_id,cnpj,tipo,status,atualizado_em) VALUES(1,'37335118000180','RFB','PENDENTE',CURRENT_TIMESTAMP)")).isInstanceOf(java.sql.SQLException.class);
            sql.execute("DELETE FROM empresas WHERE id_empresa=1");
            try (var result = sql.executeQuery("SELECT count(*) FROM empresa_comprovantes")) { result.next(); assertThat(result.getInt(1)).isZero(); }
        }
    }
}
