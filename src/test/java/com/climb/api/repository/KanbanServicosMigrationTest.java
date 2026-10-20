package com.climb.api.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class KanbanServicosMigrationTest {
    @Test void migraColunasExistentesParaPrimeiroServicoSemDuplicarTarefas() throws Exception {
        String url = "jdbc:h2:mem:kanban_servicos_migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE propostas(id_proposta BIGINT PRIMARY KEY,servico VARCHAR(50))");
            sql.execute("CREATE TABLE proposta_servicos(proposta_id BIGINT,posicao INT,servico VARCHAR(50))");
            sql.execute("CREATE TABLE contratos(id_contrato BIGINT PRIMARY KEY,proposta_id BIGINT,servico VARCHAR(50))");
            sql.execute("CREATE TABLE contrato_kanban_raias(id_raia BIGINT PRIMARY KEY,contrato_id BIGINT,posicao INT)");
            sql.execute("CREATE TABLE contrato_kanban_tasks(id_task BIGINT PRIMARY KEY,raia_id BIGINT)");
            sql.execute("INSERT INTO propostas VALUES(1,'CFO'),(2,'VALUATION')");
            sql.execute("INSERT INTO proposta_servicos VALUES(1,0,'CONTABILIDADE'),(1,1,'CFO')");
            sql.execute("INSERT INTO contratos VALUES(10,1,'CFO'),(20,NULL,'BPO'),(30,NULL,NULL),(40,2,'CFO')");
            sql.execute("INSERT INTO contrato_kanban_raias VALUES(1,10,0),(2,20,0),(3,30,0),(4,40,0)");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES(100,1)");
        }
        var flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .baselineVersion("77").target("78").load();
        flyway.baseline();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        try (var conn = DriverManager.getConnection(url, "sa", ""); var sql = conn.createStatement()) {
            try (var result = sql.executeQuery("SELECT servico FROM contrato_kanban_raias ORDER BY id_raia")) {
                result.next(); assertThat(result.getString(1)).isEqualTo("CONTABILIDADE");
                result.next(); assertThat(result.getString(1)).isEqualTo("BPO");
                result.next(); assertThat(result.getString(1)).isNull();
                result.next(); assertThat(result.getString(1)).isEqualTo("CFO");
            }
            try (var result = sql.executeQuery("SELECT id_task,raia_id FROM contrato_kanban_tasks")) {
                assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isEqualTo(100);
                assertThat(result.getLong(2)).isEqualTo(1); assertThat(result.next()).isFalse();
            }
        }
    }
}
