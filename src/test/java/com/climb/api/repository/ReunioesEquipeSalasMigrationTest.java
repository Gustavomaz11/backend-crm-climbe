package com.climb.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class ReunioesEquipeSalasMigrationTest {
    @Test void preservaClienteExistenteEAceitaReuniaoDeEquipeComSala() throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:salas;MODE=MySQL", "sa", ""); var sql = conn.createStatement()) {
            sql.execute("CREATE TABLE empresas(id BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO empresas VALUES(1)");
            sql.execute("CREATE TABLE reunioes(id BIGINT PRIMARY KEY, empresa_id BIGINT NOT NULL, FOREIGN KEY(empresa_id) REFERENCES empresas(id))");
            sql.execute("INSERT INTO reunioes VALUES(1,1)");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/migration/V73__reunioes_equipe_salas_google.sql"));
            sql.execute("INSERT INTO reunioes(id,empresa_id,sala_email) VALUES(2,NULL,'sala@resource.calendar.google.com')");
            try (var result = sql.executeQuery("SELECT empresa_id,sala_email FROM reunioes ORDER BY id")) {
                result.next(); assertThat(result.getLong(1)).isEqualTo(1L);
                result.next(); assertThat(result.getObject(1)).isNull(); assertThat(result.getString(2)).isEqualTo("sala@resource.calendar.google.com");
            }
            assertThatThrownBy(() -> sql.execute("INSERT INTO reunioes(id,empresa_id) VALUES(3,999)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
