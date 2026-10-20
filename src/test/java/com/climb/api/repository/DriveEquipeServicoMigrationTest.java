package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

class DriveEquipeServicoMigrationTest {
    @Test void preservaEquipeAntigaEDistribuiApenasVinculosComServicoIdentificado() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:drive_equipe_servico;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE propostas(id_proposta BIGINT PRIMARY KEY, servico VARCHAR(50))");
            sql.execute("CREATE TABLE proposta_servicos(proposta_id BIGINT, servico VARCHAR(50), posicao INT)");
            sql.execute("CREATE TABLE contratos(id_contrato BIGINT PRIMARY KEY, proposta_id BIGINT, servico VARCHAR(50), id_responsavel BIGINT)");
            sql.execute("CREATE TABLE contrato_equipe(id BIGINT AUTO_INCREMENT PRIMARY KEY, contrato_id BIGINT, usuario_id BIGINT)");
            sql.execute("CREATE TABLE contrato_responsaveis_servico(contrato_id BIGINT, servico VARCHAR(50), usuario_id BIGINT)");
            sql.execute("CREATE TABLE contrato_kanban_raias(id_raia BIGINT PRIMARY KEY, servico VARCHAR(50))");
            sql.execute("CREATE TABLE contrato_kanban_tasks(id_task BIGINT PRIMARY KEY, contrato_id BIGINT, raia_id BIGINT, id_responsavel BIGINT)");
            sql.execute("CREATE TABLE contrato_kanban_task_responsaveis(task_id BIGINT, usuario_id BIGINT)");
            sql.execute("CREATE TABLE empresa_drive_servicos(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO usuarios VALUES (1),(2),(3),(4)");
            sql.execute("INSERT INTO propostas VALUES (1,'CONTABILIDADE')");
            sql.execute("INSERT INTO proposta_servicos VALUES (1,'CONTABILIDADE',0),(1,'CFO',1)");
            sql.execute("INSERT INTO contratos VALUES (100,1,'CONTABILIDADE',1),(200,NULL,'BPO',4)");
            sql.execute("INSERT INTO contrato_equipe(contrato_id,usuario_id) VALUES (100,1),(100,2),(100,3),(100,4),(200,4)");
            sql.execute("INSERT INTO contrato_responsaveis_servico VALUES (100,'CONTABILIDADE',1),(100,'CFO',2)");
            sql.execute("INSERT INTO contrato_kanban_raias VALUES (10,'CONTABILIDADE')");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES (20,100,10,3)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V81__equipe_permissoes_drive_por_servico.sql"));
            var membros = new ArrayList<String>();
            try (var result = sql.executeQuery("SELECT contrato_id,servico,usuario_id FROM contrato_equipe_servico ORDER BY contrato_id,servico,usuario_id")) {
                while (result.next()) membros.add(result.getLong(1) + ":" + result.getString(2) + ":" + result.getLong(3));
            }
            assertThat(membros).containsExactly("100:CFO:2", "100:CONTABILIDADE:1", "100:CONTABILIDADE:3", "200:BPO:4");
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM contrato_equipe")) { result.next(); assertThat(result.getInt(1)).isEqualTo(5); }
            assertThatThrownBy(() -> sql.execute("INSERT INTO contrato_equipe_servico(contrato_id,usuario_id,servico) VALUES (100,2,'CFO')"))
                    .isInstanceOf(java.sql.SQLException.class);
            sql.execute("INSERT INTO contrato_equipe_servico(contrato_id,usuario_id,servico) VALUES (100,2,'CONTABILIDADE')");
        }
    }
}
