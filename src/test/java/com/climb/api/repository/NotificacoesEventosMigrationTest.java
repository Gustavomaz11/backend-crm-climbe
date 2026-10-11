package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class NotificacoesEventosMigrationTest {
    @Test void preservaAvisosEPermiteMensagemLongaEDestino() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:avisos_eventos;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE notificacoes(id BIGINT PRIMARY KEY, mensagem VARCHAR(255))");
            sql.execute("CREATE TABLE reunioes(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO notificacoes VALUES(1,'Aviso anterior')");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V65__notificacoes_tarefas_reunioes.sql"));
            sql.execute("INSERT INTO notificacoes(id,mensagem,destino) VALUES(2,'" + "a".repeat(1000) + "','/agenda?reuniaoId=3')");
            try (var result = sql.executeQuery("SELECT mensagem,destino FROM notificacoes ORDER BY id")) {
                result.next(); assertThat(result.getString(1)).isEqualTo("Aviso anterior");
                result.next(); assertThat(result.getString(1)).hasSize(1000); assertThat(result.getString(2)).isEqualTo("/agenda?reuniaoId=3");
            }
            sql.execute("INSERT INTO reunioes(id,organizador_id,link_meet) VALUES(1,2,'https://meet.google.com/abc-defg-hij')");
        }
    }
}
