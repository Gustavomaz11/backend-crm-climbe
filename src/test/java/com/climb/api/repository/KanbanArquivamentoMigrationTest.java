package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class KanbanArquivamentoMigrationTest {
    @Test
    void deveInicializarPrazoSomenteParaItensEmColunasFinaisSemApagarHistorico() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:kanban_arquivos;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE contrato_kanban_raias(id_raia BIGINT PRIMARY KEY, conclui_tarefas BOOLEAN)");
            sql.execute("INSERT INTO contrato_kanban_raias VALUES(1,TRUE),(2,FALSE)");
            sql.execute("CREATE TABLE contrato_kanban_tasks(id BIGINT, raia_id BIGINT, concluida_em TIMESTAMP, atualizado_em TIMESTAMP, criado_em TIMESTAMP)");
            sql.execute("INSERT INTO contrato_kanban_tasks VALUES(1,1,'2026-11-01 10:00:00',NULL,NULL),(2,2,NULL,'2026-11-01 10:00:00',NULL)");
            sql.execute("CREATE TABLE pipeline_vendas_tarefas(id BIGINT,status VARCHAR(20),concluido_em TIMESTAMP,atualizado_em TIMESTAMP,criado_em TIMESTAMP)");
            sql.execute("INSERT INTO pipeline_vendas_tarefas VALUES(1,'CONCLUIDA','2026-11-01 10:00:00',NULL,NULL),(2,'PENDENTE',NULL,'2026-11-01 10:00:00',NULL)");
            sql.execute("CREATE TABLE pipeline_vendas_negocios(id BIGINT,resultado VARCHAR(20),encerrado_em TIMESTAMP,ultima_movimentacao_em TIMESTAMP,criado_em TIMESTAMP)");
            sql.execute("INSERT INTO pipeline_vendas_negocios VALUES(1,'GANHO','2026-11-01 10:00:00',NULL,NULL),(2,'ABERTO',NULL,'2026-11-01 10:00:00',NULL),(3,'PERDIDO',NULL,'2026-11-01 10:00:00',NULL)");
            sql.execute("CREATE TABLE contratos(id BIGINT,etapa_preparacao VARCHAR(30),status VARCHAR(20),data_aprovacao DATE)");
            sql.execute("INSERT INTO contratos VALUES(1,'CONCLUIDO','APROVADO','2026-11-01'),(2,'EM_ANDAMENTO','PENDENTE',NULL),(3,'A_FAZER','APROVADO','2026-11-01')");
            sql.execute("CREATE TABLE reunioes(id BIGINT,status VARCHAR(20),data DATE,hora TIME)");
            sql.execute("INSERT INTO reunioes VALUES(1,'REALIZADA','2026-11-01','10:00:00'),(2,'AGENDADA','2026-11-01','10:00:00'),(3,'REALIZADO','2026-11-01','10:00:00')");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V68__arquivamento_kanbans.sql"));
            for (var table : List.of("contrato_kanban_tasks", "pipeline_vendas_tarefas", "pipeline_vendas_negocios", "contratos", "reunioes")) {
                try (var rs = sql.executeQuery("SELECT arquivar_apos,arquivado_em FROM " + table + " WHERE id=1")) {
                    rs.next(); assertThat(rs.getTimestamp(1).toLocalDateTime().toLocalDate()).isEqualTo(java.time.LocalDate.of(2026,11,16));
                    assertThat(rs.getTimestamp(2)).isNull();
                }
                try (var rs = sql.executeQuery("SELECT arquivar_apos FROM " + table + " WHERE id=2")) {
                    rs.next(); assertThat(rs.getTimestamp(1)).isNull();
                }
            }
            try (var rs = sql.executeQuery("SELECT arquivar_apos FROM pipeline_vendas_negocios WHERE id=3")) {
                rs.next(); assertThat(rs.getTimestamp(1).toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2026,11,16,10,0));
            }
            for (var table : List.of("contratos", "reunioes")) {
                try (var rs = sql.executeQuery("SELECT arquivar_apos FROM " + table + " WHERE id=3")) {
                    rs.next(); assertThat(rs.getTimestamp(1).toLocalDateTime().toLocalDate()).isEqualTo(java.time.LocalDate.of(2026,11,16));
                }
            }
        }
    }
}
