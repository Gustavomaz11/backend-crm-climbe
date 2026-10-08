package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovedGoogleUsersMigrationTest {
    @Test
    void deveRecuperarAprovadosSemModificarUsuariosExistentesOuRejeitados() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:google_migration;MODE=MySQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE usuarios (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY, nome_completo VARCHAR(255),
                          cpf VARCHAR(255) NOT NULL UNIQUE, email VARCHAR(255) UNIQUE, contato VARCHAR(255),
                          senha_hash VARCHAR(255), situacao VARCHAR(50), cargo_id BIGINT);
                        CREATE TABLE oauth2_pending_registrations (
                          id BIGINT PRIMARY KEY, provider VARCHAR(50), provider_user_id VARCHAR(255),
                          nome VARCHAR(255), email VARCHAR(255), aprovado BOOLEAN, consumido BOOLEAN,
                          cargo_id BIGINT, aprovado_em TIMESTAMP, avatar_url VARCHAR(500),
                          access_token_criptografado VARCHAR(4096), refresh_token_criptografado VARCHAR(4096),
                          access_token_expira_em TIMESTAMP, scopes VARCHAR(1000));
                        CREATE TABLE oauth2_pending_permissoes (pending_id BIGINT, id_permissao BIGINT);
                        CREATE TABLE usuario_oauth (
                          usuario_id BIGINT, provider VARCHAR(50), provider_user_id VARCHAR(255),
                          email_provider VARCHAR(255), nome_provider VARCHAR(255), avatar_url VARCHAR(500),
                          vinculado_em TIMESTAMP, access_token_criptografado VARCHAR(4096),
                          refresh_token_criptografado VARCHAR(4096), access_token_expira_em TIMESTAMP,
                          scopes VARCHAR(1000), UNIQUE(provider, provider_user_id));
                        CREATE TABLE usuario_permissoes (id_usuario BIGINT, id_permissao BIGINT,
                          PRIMARY KEY(id_usuario, id_permissao));
                        INSERT INTO usuarios VALUES
                          (1,'Ativo','11111111111','ativo@test.com','','hash-ativo','ATIVO',2),
                          (2,'Revogado','22222222222','revogado@test.com','','hash-revogado','REVOGADO',3);
                        INSERT INTO usuario_permissoes VALUES (1,99),(2,98);
                        INSERT INTO oauth2_pending_registrations
                          (id,provider,provider_user_id,nome,email,aprovado,consumido,cargo_id) VALUES
                          (10,'GOOGLE','novo-antigo','Novo','novo@test.com',TRUE,FALSE,4),
                          (11,'GOOGLE','novo','Novo','novo@test.com',TRUE,FALSE,4),
                          (12,'GOOGLE','ativo','Ativo','ativo@test.com',TRUE,FALSE,5),
                          (13,'GOOGLE','revogado','Revogado','revogado@test.com',TRUE,FALSE,5),
                          (14,'GOOGLE','recusado','Recusado','recusado@test.com',FALSE,TRUE,5),
                          (15,'GOOGLE','pendente','Pendente','pendente@test.com',FALSE,FALSE,5);
                        INSERT INTO oauth2_pending_permissoes VALUES (10,7),(11,8),(11,9),(12,8),(13,8),(14,8);
                        """);
            }
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V55__provision_approved_google_users.sql"));

            assertEquals(3, count(connection, "SELECT COUNT(*) FROM usuarios"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM usuarios WHERE email='novo@test.com' AND cpf IS NULL AND situacao='COMPLETAR_CADASTRO' AND cargo_id=4"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM usuario_oauth WHERE provider_user_id='novo'"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM usuario_permissoes up JOIN usuarios u ON u.id=up.id_usuario WHERE u.email='novo@test.com'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM usuarios WHERE id=1 AND situacao='ATIVO' AND senha_hash='hash-ativo' AND cargo_id=2"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM usuarios WHERE id=2 AND situacao='REVOGADO' AND senha_hash='hash-revogado' AND cargo_id=3"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM usuario_permissoes WHERE id_permissao IN (98,99)"));
        }
    }

    private int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
