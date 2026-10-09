package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class KanbanPrazosMigrationTest {
    @Test void preservaTarefasEAdicionaAuditoriaEDeduplicacao() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:kanban_prazos;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE contrato_kanban_tasks (id_task BIGINT PRIMARY KEY, data_fim DATE, raia_id BIGINT)");
            sql.execute("CREATE TABLE pipeline_vendas_tarefas (id_tarefa BIGINT PRIMARY KEY, prazo DATE)");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES (1,'2026-11-10',2)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V64__kanban_prazos_atrasos.sql"));
            sql.execute("UPDATE contrato_kanban_tasks SET justificativa_atraso='Aguardando documentos',justificada_em=CURRENT_TIMESTAMP,justificada_por=3 WHERE id_task=1");
            try (var r = sql.executeQuery("SELECT data_fim,justificativa_atraso,justificada_por FROM contrato_kanban_tasks WHERE id_task=1")) {
                r.next(); assertThat(r.getDate(1).toString()).isEqualTo("2026-11-10"); assertThat(r.getString(2)).isEqualTo("Aguardando documentos"); assertThat(r.getLong(3)).isEqualTo(3);
            }
            sql.execute("INSERT INTO kanban_prazo_notificacoes(chave,enviado_em) VALUES ('CONTRATO:1:2026-11-10:0:3',CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> sql.execute("INSERT INTO kanban_prazo_notificacoes(chave,enviado_em) VALUES ('CONTRATO:1:2026-11-10:0:3',CURRENT_TIMESTAMP)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
