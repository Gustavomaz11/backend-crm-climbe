package com.climb.api.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class FlywayMigrationDiscoveryTest {
    @Test void descobreTodasAsMigrationsSemConflitosDeVersao() {
        var flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:flyway_discovery;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration").load();

        assertThatCode(() -> flyway.info().all()).doesNotThrowAnyException();
    }

    @Test void aplicaDriveAposVersao76PreservandoCadastrosExistentes() throws Exception {
        String url = "jdbc:h2:mem:flyway_drive_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE empresas(id_empresa BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO empresas VALUES(1)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V74__empresa_dados_cadastrais_comprovantes.sql"));
            sql.execute("INSERT INTO empresa_comprovantes(empresa_id,cnpj,tipo,status,atualizado_em) VALUES(1,'37335118000180','RFB','DISPONIVEL',CURRENT_TIMESTAMP)");
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").baselineVersion("76").target("77").load();
        flyway.baseline();

        assertThat(flyway.info().pending()).extracting(MigrationInfo::getScript)
                .containsExactly("V77__drive_empresas_servicos.sql");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("77");
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            sql.execute("INSERT INTO empresa_drive(empresa_id,tentar_em) VALUES(1,CURRENT_TIMESTAMP)");
            try (var result = sql.executeQuery("SELECT status,drive_arquivo_id FROM empresa_comprovantes")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("DISPONIVEL");
                assertThat(result.getString(2)).isNull();
            }
        }
    }
}
