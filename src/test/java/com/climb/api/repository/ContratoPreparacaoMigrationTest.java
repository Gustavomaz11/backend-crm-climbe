package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ContratoPreparacaoMigrationTest {
    @Test
    void deveCorrigirEnviosAntigosPreservandoAjustesDoClienteEOutrasEtapas() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:contrato_preparacao;MODE=MySQL", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE contratos (id_contrato BIGINT PRIMARY KEY, status VARCHAR(30), url_pdf VARCHAR(255), etapa_preparacao VARCHAR(30))");
            statement.execute("CREATE TABLE revisoes_documento (tipo VARCHAR(20), referencia_id BIGINT, status VARCHAR(30))");
            statement.execute("""
                    INSERT INTO contratos VALUES
                    (1, 'PENDENTE', 'contrato.pdf', 'REVISAO'),
                    (2, 'PENDENTE', 'contrato.pdf', 'REVISAO'),
                    (3, 'REJEITADO', 'contrato.pdf', 'REVISAO'),
                    (4, 'APROVADO', 'contrato.pdf', 'REVISAO'),
                    (5, 'PENDENTE', 'contrato.pdf', NULL),
                    (6, 'PENDENTE', NULL, NULL),
                    (7, 'PENDENTE', 'contrato.pdf', 'A_FAZER'),
                    (8, 'PENDENTE', 'contrato.pdf', 'EM_ANDAMENTO'),
                    (9, 'PENDENTE', 'contrato.pdf', NULL),
                    (10, 'PENDENTE', '   ', 'REVISAO'),
                    (11, 'PENDENTE', 'contrato.pdf', 'REVISAO'),
                    (12, 'APROVADO', 'contrato.pdf', 'CONCLUIDO')
                    """);
            statement.execute("""
                    INSERT INTO revisoes_documento VALUES
                    ('CONTRATO', 1, 'AGUARDANDO_CLIENTE'), ('CONTRATO', 2, 'AJUSTES_SOLICITADOS'),
                    ('CONTRATO', 3, 'REPROVADO'), ('CONTRATO', 4, 'AJUSTES_SOLICITADOS'),
                    ('CONTRATO', 5, 'AGUARDANDO_CLIENTE'), ('CONTRATO', 8, 'AJUSTES_SOLICITADOS'),
                    ('CONTRATO', 9, 'AJUSTES_SOLICITADOS'), ('PROPOSTA', 11, 'AJUSTES_SOLICITADOS')
                    """);
            var migration = new ClassPathResource("db/migration/V61__corrigir_coluna_revisao_contratos.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);
            var etapas = new ArrayList<String>();
            try (var result = statement.executeQuery("SELECT etapa_preparacao FROM contratos ORDER BY id_contrato")) {
                while (result.next()) etapas.add(result.getString(1));
            }
            assertThat(etapas).containsExactly("EM_ANDAMENTO", "REVISAO", "EM_ANDAMENTO", "CONCLUIDO",
                    "EM_ANDAMENTO", "A_FAZER", "A_FAZER", "EM_ANDAMENTO", "REVISAO", "A_FAZER",
                    "EM_ANDAMENTO", "CONCLUIDO");
        }
    }
}
