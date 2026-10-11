package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.repository.NotificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(NotificacaoEntregaServiceTest.Config.class)
class NotificacaoEntregaServiceTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean NotificacaoRepository repository() { return mock(NotificacaoRepository.class); }
        @Bean EmailService email() { return mock(EmailService.class); }
        @Bean NotificacaoEntregaService entrega(NotificacaoRepository repo, ApplicationEventPublisher events, EmailService email) {
            return new NotificacaoEntregaService(repo, events, email, "https://app.example.test/");
        }
        @Bean AbstractPlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) {}
                protected void doCommit(DefaultTransactionStatus status) {}
                protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }
    @Autowired NotificacaoEntregaService entrega;
    @Autowired EmailService email;
    @Autowired NotificacaoRepository repository;
    @Autowired AbstractPlatformTransactionManager manager;
    @BeforeEach void limpar() { reset(email, repository); }
    @Test void enviaEmailApenasDepoisDaConfirmacaoDaOperacao() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            notificar(); verify(repository).save(any()); verifyNoInteractions(email);
        });
        verify(email).enviarEmailComBotao(eq("pessoa@example.test"), eq("Atribuição"), eq("Atribuição"), eq("Você foi atribuído"), anyString(), eq("https://app.example.test/contratos/kanban?contrato=1&tarefaId=2"), anyString());
    }
    @Test void rollbackNaoEnviaEmailDeUmaTarefaNaoSalva() {
        new TransactionTemplate(manager).executeWithoutResult(status -> { notificar(); status.setRollbackOnly(); });
        verifyNoInteractions(email);
    }
    private void notificar() {
        Usuario usuario = new Usuario(); usuario.setId(1L); usuario.setEmail("pessoa@example.test");
        entrega.notificar(usuario, "TAREFA_ATRIBUIDA", "Atribuição", "Você foi atribuído", "/contratos/kanban?contrato=1&tarefaId=2");
    }
}
