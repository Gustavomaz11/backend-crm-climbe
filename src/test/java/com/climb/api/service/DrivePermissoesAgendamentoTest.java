package com.climb.api.service;

import com.climb.api.repository.EmpresaDriveServicoRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(DrivePermissoesAgendamentoTest.Config.class)
class DrivePermissoesAgendamentoTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean EmpresaDriveServicoRepository pastas() { return mock(EmpresaDriveServicoRepository.class); }
        @Bean DrivePermissoesAgendamentoService fila(EmpresaDriveServicoRepository pastas) {
            return new DrivePermissoesAgendamentoService(pastas);
        }
        @Bean PlatformTransactionManager transactionManager() {
            var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:drive_fila_commit");
            return new DataSourceTransactionManager(source);
        }
    }
    @Autowired EmpresaDriveServicoRepository pastas;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher eventos;
    @BeforeEach void limpar() { reset(pastas); }

    @Test void alteracaoSoEntraNaFilaDepoisDoCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventos.publishEvent(new DrivePermissoesAlteradas(100L));
            verifyNoInteractions(pastas);
        });
        verify(pastas).agendarPermissoes(eq(100L), any());
    }
    @Test void transacaoDesfeitaNaoAlteraPermissoes() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventos.publishEvent(new DrivePermissoesAlteradas(100L));
            status.setRollbackOnly();
        });
        verifyNoInteractions(pastas);
    }
    @Test void alteracaoDeUsuarioAgendaRevisaoDeTodasAsPastas() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventos.publishEvent(new DrivePermissoesAlteradas(null)));
        verify(pastas).agendarPermissoes(isNull(), any());
    }
}
