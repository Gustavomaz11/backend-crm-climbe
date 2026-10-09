package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class TarefasColaboracaoMigrationTest {
    @Test void devePreservarResponsaveisEValidarVinculosComCascade() throws Exception {
        try (var conexao = DriverManager.getConnection("jdbc:h2:mem:tarefas_colaboracao;MODE=MySQL", "sa", ""); var sql = conexao.createStatement()) {
            sql.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO usuarios VALUES (1), (2)");
            sql.execute("CREATE TABLE contrato_kanban_tasks (id_task BIGINT PRIMARY KEY, id_responsavel BIGINT)");
            sql.execute("CREATE TABLE pipeline_vendas_tarefas (id_tarefa BIGINT PRIMARY KEY, responsavel_id BIGINT)");
            sql.execute("CREATE TABLE contrato_kanban_subtarefas (id_subtarefa BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE pipeline_vendas_subtarefas (id_subtarefa BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES (10, 1), (11, NULL)");
            sql.execute("INSERT INTO pipeline_vendas_tarefas VALUES (20, 2)");
            ScriptUtils.executeSqlScript(conexao, new ClassPathResource("db/migration/V57__tarefas_responsaveis_comentarios_anexos.sql"));
            ScriptUtils.executeSqlScript(conexao, new ClassPathResource("db/migration/V58__pastas_de_anexos_das_tarefas.sql"));
            try (var result = sql.executeQuery("SELECT usuario_id FROM contrato_kanban_task_responsaveis WHERE task_id = 10")) { assertTrue(result.next()); assertEquals(1, result.getLong(1)); }
            try (var result = sql.executeQuery("SELECT usuario_id FROM pipeline_tarefa_responsaveis WHERE tarefa_id = 20")) { assertTrue(result.next()); assertEquals(2, result.getLong(1)); }
            sql.execute("INSERT INTO contrato_kanban_task_responsaveis VALUES (10, 2)");
            sql.execute("INSERT INTO tarefa_comentarios (id,contrato_task_id,autor_id,conteudo,criado_em) VALUES (30,10,1,'Comentário',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO tarefa_comentarios (id,contrato_task_id,autor_id,comentario_pai_id,conteudo,criado_em) VALUES (31,10,2,30,'Resposta',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO tarefa_anexos (contrato_task_id,comentario_id,autor_id,nome,content_type,tamanho,chave,criado_em) VALUES (10,31,2,'balanco.pdf','application/pdf',10,'chave',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO tarefa_pastas (id,contrato_task_id,autor_id,nome,criado_em) VALUES (50,10,1,'Documentos',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO tarefa_pastas (id,contrato_task_id,pasta_pai_id,autor_id,nome,criado_em) VALUES (51,10,50,1,'2026',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO tarefa_pastas (id,pipeline_tarefa_id,autor_id,nome,criado_em) VALUES (52,20,2,'Comercial',CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO tarefa_anexos (contrato_task_id,pasta_id,autor_id,nome,content_type,tamanho,chave,criado_em) VALUES (10,51,2,'outro.pdf','application/pdf',10,'outra_chave',CURRENT_TIMESTAMP)");
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO tarefa_anexos (contrato_task_id,pasta_id,comentario_id,autor_id,nome,content_type,tamanho,chave,criado_em) VALUES (10,51,31,2,'invalido.pdf','application/pdf',10,'chave',CURRENT_TIMESTAMP)"));
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO tarefa_pastas (autor_id,nome,criado_em) VALUES (1,'Sem tarefa',CURRENT_TIMESTAMP)"));
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO tarefa_comentarios (autor_id,conteudo,criado_em) VALUES (1,'Sem tarefa',CURRENT_TIMESTAMP)"));
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO tarefa_comentarios (contrato_task_id,pipeline_tarefa_id,autor_id,conteudo,criado_em) VALUES (10,20,1,'Duas tarefas',CURRENT_TIMESTAMP)"));
            sql.execute("DELETE FROM contrato_kanban_tasks WHERE id_task = 10");
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM tarefa_anexos")) { result.next(); assertEquals(0, result.getInt(1)); }
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM tarefa_comentarios")) { result.next(); assertEquals(0, result.getInt(1)); }
            try (var result = sql.executeQuery("SELECT COUNT(*) FROM tarefa_pastas")) { result.next(); assertEquals(1, result.getInt(1)); }
        }
    }
}
