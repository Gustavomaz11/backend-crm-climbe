package com.climb.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class ContratoResponsaveisServicoMigrationTest {
    @Test
    void preservaResponsavelLegadoEPermiteUmaPessoaEmVariosServicos() throws Exception {
        String url = "jdbc:h2:mem:responsaveis_servico_migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE contratos(id_contrato BIGINT PRIMARY KEY,id_responsavel BIGINT)");
            sql.execute("INSERT INTO usuarios VALUES(1),(2)");
            sql.execute("INSERT INTO contratos VALUES(10,1)");
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .baselineVersion("78").target("79").load();
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            sql.execute("INSERT INTO contrato_responsaveis_servico VALUES(10,'CONTABILIDADE',2),(10,'CFO',2)");
            assertThatThrownBy(() -> sql.execute("INSERT INTO contrato_responsaveis_servico VALUES(10,'CFO',1)"))
                    .isInstanceOf(SQLException.class);
            try (var result = sql.executeQuery("SELECT id_responsavel FROM contratos WHERE id_contrato=10")) {
                assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isEqualTo(1);
            }
        }
    }
}
