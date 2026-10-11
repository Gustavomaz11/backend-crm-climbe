package com.climb.api.repository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class AuthSecurityMigrationTest {
    @Test void criaSessoesEStateComChavesEstrangeirasECatalogoGranular() throws Exception {
        try(var conn=DriverManager.getConnection("jdbc:h2:mem:auth_migrations;MODE=MySQL", "sa", ""); var sql=conn.createStatement()) {
            sql.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO usuarios VALUES(7)");
            sql.execute("CREATE TABLE oauth2_exchange_codes(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE permissoes(id_permissao BIGINT AUTO_INCREMENT PRIMARY KEY,codigo VARCHAR(100) UNIQUE,descricao VARCHAR(255))");
            ScriptUtils.executeSqlScript(conn,new ClassPathResource("db/migration/V70__seguranca_sessoes_oauth.sql"));
            var grants=new ClassPathResource("db/migration/V71__permissoes_planilhas.sql");
            ScriptUtils.executeSqlScript(conn,grants);ScriptUtils.executeSqlScript(conn,grants);
            try(var row=sql.executeQuery("SELECT versao_autenticacao FROM usuarios WHERE id=7")) { row.next();assertThat(row.getLong(1)).isZero(); }
            try(var row=sql.executeQuery("SELECT COUNT(*) FROM permissoes WHERE codigo LIKE 'PLANILHA_%'")) { row.next();assertThat(row.getInt(1)).isEqualTo(5); }
            sql.execute("INSERT INTO sessoes_autenticacao VALUES('sessao',7,0,'hash',CURRENT_TIMESTAMP,DATEADD('DAY',7,CURRENT_TIMESTAMP),NULL)");
            sql.execute("DELETE FROM usuarios WHERE id=7");
            try(var row=sql.executeQuery("SELECT COUNT(*) FROM sessoes_autenticacao")) { row.next();assertThat(row.getInt(1)).isZero(); }
        }
    }
}
