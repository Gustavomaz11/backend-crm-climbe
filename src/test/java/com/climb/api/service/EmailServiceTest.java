package com.climb.api.service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

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
