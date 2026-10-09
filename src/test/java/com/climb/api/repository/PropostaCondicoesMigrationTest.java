package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PropostaCondicoesMigrationTest {
    @Test
    void deveCriarTabelasComValoresPorServicoERecebimentosSemDuplicidade() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:proposta_condicoes;MODE=MySQL", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE propostas (id_proposta BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO propostas VALUES (1)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V56__proposta_servicos_recebimentos.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V59__proposta_recebimentos_por_servico.sql"));
            statement.execute("INSERT INTO proposta_servicos VALUES (1, 0, 'BPO', 30000, 25, 20), (1, 1, 'CFO', 20000, 30, 10)");
            statement.execute("INSERT INTO proposta_recebimentos VALUES (1, 1, 3000), (1, 2, 3000)");
            statement.execute("INSERT INTO proposta_recebimento_servicos VALUES (1, 1, 'BPO', 1800), (1, 1, 'CFO', 1200), (1, 2, 'BPO', 3000), (1, 2, 'CFO', 0)");
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO proposta_recebimento_servicos VALUES (1, 1, 'BPO', 1)"));
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO proposta_recebimento_servicos VALUES (1, 3, 'BPO', -1)"));
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO proposta_recebimento_servicos VALUES (999, 1, 'BPO', 1)"));
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO proposta_recebimentos VALUES (1, 1, 4400)"));
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO proposta_servicos VALUES (1, 2, 'BPO', 1, 0, 0)"));
            try (var result = statement.executeQuery("SELECT SUM(valor) FROM proposta_servicos")) {
                result.next();
                assertEquals(new java.math.BigDecimal("50000.00"), result.getBigDecimal(1));
            }
            statement.execute("DELETE FROM propostas WHERE id_proposta = 1");
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM proposta_recebimentos")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM proposta_recebimento_servicos")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
        }
    }
}
