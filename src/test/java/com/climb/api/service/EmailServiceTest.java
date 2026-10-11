package com.climb.api.service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void deveEncaminharPropostaParaHotmailSemRestringirODominio() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mensagem);
        EmailService service = new EmailService(mailSender, "contato@climbe.com.br", "Climbe", "", "https://www.crmclimbe.com.br");

        assertTrue(service.enviarEmailComBotao("cliente@hotmail.com", "Documento para análise", "Proposta",
                "Olá! Sua proposta está disponível para revisão.", "Revisar documento", "https://www.crmclimbe.com.br/revisao/token-teste", "Climbe"));

        assertEquals("cliente@hotmail.com", ((InternetAddress) mensagem.getAllRecipients()[0]).getAddress());
        assertTrue(mensagem.getContent().toString().contains("https://www.crmclimbe.com.br/revisao/token-teste"));
        verify(mailSender).send(mensagem);
    }

    @Test
    void deveInformarFalhaQuandoOServidorSmtpRecusarOEnvio() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mensagem);
        doThrow(new MailSendException("550 5.7.1 envio recusado")).when(mailSender).send(mensagem);
        EmailService service = new EmailService(mailSender, "contato@climbe.com.br", "Climbe", "", "https://www.crmclimbe.com.br");

        assertFalse(service.enviarEmailHtml("cliente@hotmail.com", "Documento para análise", "<p>Proposta</p>"));
    }

    @Test
    void deveConfigurarRemetenteEReplyToParaEnvioPeloResend() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mensagem);

        EmailService emailService = new EmailService(
                mailSender,
                "notificacoes@envios.crmclimbe.com.br",
                "Climbe",
                "contato@climbe.com.br",
                "https://www.crmclimbe.com.br"
        );

        boolean enviado = emailService.enviarEmailHtml(
                "cliente@empresa.com.br",
                "Documento para analise",
                "<strong>Documento disponivel</strong>"
        );

        InternetAddress remetente = (InternetAddress) mensagem.getFrom()[0];
        InternetAddress responderPara = (InternetAddress) mensagem.getReplyTo()[0];

        assertTrue(enviado);
        assertEquals("notificacoes@envios.crmclimbe.com.br", remetente.getAddress());
        assertEquals("Climbe", remetente.getPersonal());
        assertEquals("contato@climbe.com.br", responderPara.getAddress());
        verify(mailSender).send(mensagem);
    }
}
