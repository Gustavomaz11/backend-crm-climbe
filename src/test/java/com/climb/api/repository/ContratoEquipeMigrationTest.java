package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContratoEquipeMigrationTest {
    @Test
    void migrationExigeSelecaoDaEquipeEImpedeParticipanteDuplicadoNoMes() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:contrato_equipe_migration;MODE=MySQL", "sa", "");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE contratos (id_contrato BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE contrato_kanban_raias (id_raia BIGINT PRIMARY KEY, titulo VARCHAR(100))");
            sql.execute("CREATE TABLE contrato_kanban_tasks (id_task BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO usuarios VALUES (1), (2)");
            sql.execute("INSERT INTO contratos VALUES (1)");
            sql.execute("INSERT INTO contrato_kanban_raias VALUES (1, 'Concluído'), (2, 'Em andamento')");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES (1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V62__contrato_equipe_kanban.sql"));
            try (var result = sql.executeQuery("SELECT equipe_configurada FROM contratos WHERE id_contrato=1")) {
                result.next(); assertThat(result.getBoolean(1)).isFalse();
            }
            try (var result = sql.executeQuery("SELECT conclui_tarefas FROM contrato_kanban_raias ORDER BY id_raia")) {
                result.next(); assertThat(result.getBoolean(1)).isTrue();
                result.next(); assertThat(result.getBoolean(1)).isFalse();
            }
            sql.execute("INSERT INTO contrato_equipe(contrato_id,usuario_id) VALUES (1,1)");
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO contrato_equipe(contrato_id,usuario_id) VALUES (1,1)"));
            sql.execute("INSERT INTO contrato_tarefa_apoios VALUES (1,2)");
            sql.execute("INSERT INTO contrato_rateio_tecnico_participantes(contrato_id,usuario_id,competencia) VALUES (1,2,'2026-10-01')");
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO contrato_rateio_tecnico_participantes(contrato_id,usuario_id,competencia) VALUES (1,2,'2026-10-01')"));
            sql.execute("INSERT INTO contrato_rateio_tecnico_participantes(contrato_id,usuario_id,competencia) VALUES (1,2,'2026-11-01')");
            sql.execute("DELETE FROM contrato_kanban_tasks WHERE id_task=1");
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM contrato_tarefa_apoios")) {
                result.next(); assertThat(result.getInt(1)).isZero();
            }
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM contrato_rateio_tecnico_participantes")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(2);
            }
        }
    }
}
