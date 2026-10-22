package com.climb.api.integracao;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class IntegracaoMigrationTest {
    @Test void adicionaPayloadSemPreencherEventosAntigos() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:payload_migration;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE integracao_auditoria(id BIGINT PRIMARY KEY, evento VARCHAR(100))");
            sql.execute("INSERT INTO integracao_auditoria VALUES(1,'REQUISICAO')");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V84__payload_auditoria_integracoes.sql"));
            try (var rs = sql.executeQuery("SELECT payload_requisicao,payload_limite_excedido FROM integracao_auditoria WHERE id=1")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isNull(); assertThat(rs.getBoolean(2)).isFalse();
            }
            sql.execute("INSERT INTO integracao_auditoria(id,evento,payload_requisicao) VALUES(2,'REQUISICAO','{\"responsavelId\":0}')");
        }
    }
    @Test void permiteLeadSemResponsavelPreservandoVinculosExistentesEChaveEstrangeira() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:lead_migration;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE pipeline_vendas_negocios(id_negocio BIGINT PRIMARY KEY, responsavel_id BIGINT NOT NULL REFERENCES usuarios(id))");
            sql.execute("INSERT INTO usuarios VALUES(1)");
            sql.execute("INSERT INTO pipeline_vendas_negocios VALUES(1,1)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V83__leads_sem_responsavel.sql"));
            sql.execute("INSERT INTO pipeline_vendas_negocios VALUES(2,NULL)");
            try (var rs = sql.executeQuery("SELECT responsavel_id FROM pipeline_vendas_negocios WHERE id_negocio=1")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getLong(1)).isEqualTo(1);
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO pipeline_vendas_negocios VALUES(3,0)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
    @Test void migraEstruturaEConcedeGestaoSomenteAAdministradoresExistentes() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:integracao_migration;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE empresas(id_empresa BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE permissoes(id_permissao BIGINT AUTO_INCREMENT PRIMARY KEY, codigo VARCHAR(100) UNIQUE, descricao VARCHAR(255))");
            sql.execute("CREATE TABLE usuario_permissoes(id BIGINT AUTO_INCREMENT PRIMARY KEY, id_usuario BIGINT, id_permissao BIGINT)");
            sql.execute("INSERT INTO usuarios VALUES(1),(2)");
            sql.execute("INSERT INTO permissoes(codigo,descricao) VALUES('PERMITIR_ACESSO','Administrador'),('CONTRATO_LER','Leitura')");
            sql.execute("INSERT INTO usuario_permissoes(id_usuario,id_permissao) VALUES(1,1),(2,2)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V82__tokens_integracoes.sql"));
            try (var rs = sql.executeQuery("SELECT up.id_usuario FROM usuario_permissoes up JOIN permissoes p ON p.id_permissao=up.id_permissao WHERE p.codigo='INTEGRACOES_GERENCIAR'")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getLong(1)).isEqualTo(1); assertThat(rs.next()).isFalse();
            }
            for (var table : java.util.List.of("INTEGRACAO_CLIENTES", "INTEGRACAO_CLIENTE_EMPRESAS", "INTEGRACAO_CLIENTE_OPERACOES", "INTEGRACAO_CLIENTE_METODOS", "INTEGRACAO_AUDITORIA")) {
                try (var rs = conn.getMetaData().getTables(null, null, table, null)) { assertThat(rs.next()).as(table).isTrue(); }
            }
        }
    }
}
