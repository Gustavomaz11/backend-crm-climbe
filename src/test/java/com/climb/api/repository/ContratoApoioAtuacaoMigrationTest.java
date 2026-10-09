package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class ContratoApoioAtuacaoMigrationTest {
    @Test void recuperaVinculosLegadosEPreservaPeriodosAoExcluirTarefa() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:apoio_atuacoes;MODE=MySQL", "sa", "");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE contratos (id_contrato BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE contrato_kanban_raias (id_raia BIGINT PRIMARY KEY, conclui_tarefas BOOLEAN)");
            sql.execute("CREATE TABLE contrato_kanban_tasks (id_task BIGINT PRIMARY KEY, contrato_id BIGINT, raia_id BIGINT, id_responsavel BIGINT, criado_em DATETIME, concluida_em DATETIME)");
            sql.execute("CREATE TABLE contrato_tarefa_apoios (task_id BIGINT, usuario_id BIGINT)");
            sql.execute("CREATE TABLE contrato_kanban_task_responsaveis (task_id BIGINT, usuario_id BIGINT)");
            sql.execute("INSERT INTO usuarios VALUES (1),(2),(3)"); sql.execute("INSERT INTO contratos VALUES (10)");
            sql.execute("INSERT INTO contrato_kanban_raias VALUES (1,FALSE),(2,TRUE)");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES (20,10,2,2,'2026-11-15 09:00:00','2026-12-08 09:00:00'),(21,10,1,2,'2026-11-20 09:00:00',NULL)");
            sql.execute("INSERT INTO contrato_tarefa_apoios VALUES (20,2),(20,3),(21,2),(21,1)");
            sql.execute("INSERT INTO contrato_kanban_task_responsaveis VALUES (20,2),(20,3),(21,2)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V63__contrato_apoio_periodos_atuacao.sql"));
            try (var result = sql.executeQuery("SELECT task_id,usuario_id,inicio,fim FROM contrato_apoio_atuacoes ORDER BY task_id,usuario_id")) {
                result.next(); assertThat(result.getLong(2)).isEqualTo(2);
                assertThat(result.getTimestamp(3).toLocalDateTime().toString()).isEqualTo("2026-11-15T09:00");
                assertThat(result.getTimestamp(4).toLocalDateTime().toString()).isEqualTo("2026-12-08T09:00");
                result.next(); assertThat(result.getLong(2)).isEqualTo(3);
                result.next(); assertThat(result.getLong(1)).isEqualTo(21); assertThat(result.getTimestamp(4)).isNull();
                assertThat(result.next()).isFalse();
            }
            sql.execute("DELETE FROM contrato_kanban_tasks WHERE id_task=20");
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM contrato_apoio_atuacoes WHERE task_id IS NULL AND fim IS NOT NULL")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }
}
