package com.climb.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class ContratoServicoEncerramentoMigrationTest {
    @Test
    void migraSemEncerrarContratosExistentesEPreservaAuditoriaPorServico() throws Exception {
        String url = "jdbc:h2:mem:encerramento_migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE contratos(id_contrato BIGINT PRIMARY KEY, status VARCHAR(30))");
            sql.execute("INSERT INTO usuarios VALUES(1)");
            sql.execute("INSERT INTO contratos VALUES(10,'APROVADO')");
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .baselineVersion("79").target("80").load();
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            try (var result = sql.executeQuery("SELECT count(*) FROM contrato_servicos_encerrados")) {
                result.next(); assertThat(result.getInt(1)).isZero();
            }
            sql.execute("INSERT INTO contrato_servicos_encerrados VALUES(10,'CFO',CURRENT_TIMESTAMP,1),(10,'CONTABILIDADE',CURRENT_TIMESTAMP,1)");
            assertThatThrownBy(() -> sql.execute("INSERT INTO contrato_servicos_encerrados VALUES(10,'CFO',CURRENT_TIMESTAMP,1)"))
                    .isInstanceOf(SQLException.class);
            try (var result = sql.executeQuery("SELECT status FROM contratos WHERE id_contrato=10")) {
                result.next(); assertThat(result.getString(1)).isEqualTo("APROVADO");
            }
        }
    }
}
