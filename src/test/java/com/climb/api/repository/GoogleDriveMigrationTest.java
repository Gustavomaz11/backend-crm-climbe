package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class GoogleDriveMigrationTest {
    @Test void preservaDadosExistentesImpedeDuplicarServicoEExcluiVinculosAoExcluirEmpresa() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:drive_migration;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE empresas(id_empresa BIGINT PRIMARY KEY)"); sql.execute("INSERT INTO empresas VALUES(1)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V74__empresa_dados_cadastrais_comprovantes.sql"));
            sql.execute("INSERT INTO empresa_comprovantes(empresa_id,cnpj,tipo,status,atualizado_em) VALUES(1,'37335118000180','RFB','DISPONIVEL',CURRENT_TIMESTAMP)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V77__drive_empresas_servicos.sql"));
            sql.execute("INSERT INTO empresa_drive(empresa_id,tentar_em) VALUES(1,CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO empresa_drive_servicos(empresa_id,servico) VALUES(1,'BPO')");
            assertThatThrownBy(() -> sql.execute("INSERT INTO empresa_drive_servicos(empresa_id,servico) VALUES(1,'BPO')")).isInstanceOf(java.sql.SQLException.class);
            try (var result = sql.executeQuery("SELECT status,drive_arquivo_id FROM empresa_comprovantes")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("DISPONIVEL"); assertThat(result.getString(2)).isNull();
            }
            sql.execute("DELETE FROM empresas WHERE id_empresa=1");
            for (String tabela : new String[]{"empresa_drive", "empresa_drive_servicos", "empresa_comprovantes"}) {
                try (var result = sql.executeQuery("SELECT count(*) FROM " + tabela)) { result.next(); assertThat(result.getInt(1)).isZero(); }
            }
        }
    }
}
