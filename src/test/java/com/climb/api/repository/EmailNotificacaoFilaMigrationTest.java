package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class EmailNotificacaoFilaMigrationTest {
    @Test
    void criaFilaPersistenteComIndiceParaProximosEnvios() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:email_fila;MODE=MySQL", "sa", "");
             var sql = connection.createStatement()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V72__fila_emails_notificacao.sql"));
            sql.execute("""
                    INSERT INTO emails_notificacao_pendentes
                        (destinatario, assunto, mensagem, destino, criado_em, proxima_tentativa)
                    VALUES ('usuario@fila.test', 'Tarefa atribuída', 'Nova tarefa', '/kanban?taskId=3',
                        '2026-10-11 15:00:00.123456', '2026-10-11 15:00:00.123456')
                    """);
            try (var result = sql.executeQuery("SELECT id, tentativas, proxima_tentativa FROM emails_notificacao_pendentes")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("id")).isPositive();
                assertThat(result.getInt("tentativas")).isZero();
                assertThat(result.getTimestamp("proxima_tentativa").getNanos()).isEqualTo(123456000);
            }
            try (var indices = connection.getMetaData().getIndexInfo(null, null, "EMAILS_NOTIFICACAO_PENDENTES", false, false)) {
                boolean encontrado = false;
                while (indices.next()) {
                    if ("IDX_EMAIL_NOTIFICACAO_PROXIMA".equals(indices.getString("INDEX_NAME"))) encontrado = true;
                }
                assertThat(encontrado).isTrue();
            }
        }
    }
}
