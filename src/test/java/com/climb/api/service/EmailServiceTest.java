package com.climb.api.service;

import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void enviaDocumentacaoPdfAnexaSemIncluirSegredoNoEmail() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var service = new EmailService(sender, "contato@climbe.com.br", "Climbe", "");
        byte[] pdf = "%PDF-exemplo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(service.enviarEmailComPdf("responsavel@example.com", "Integração", "Ativação", "CLIENT_ID: exemplo",
                "Obter segredo", "https://crm.example.com/integracoes/ativar#token=exemplo", pdf));
        message.saveChanges(); assertTrue(message.isMimeType("multipart/mixed"));
        var mixed = (Multipart) message.getContent();
        var attachment = mixed.getBodyPart(mixed.getCount() - 1);
        assertEquals("documentacao-integracao-climbe.pdf", attachment.getFileName());
        assertEquals(Part.ATTACHMENT, attachment.getDisposition());
        assertArrayEquals(pdf, attachment.getInputStream().readAllBytes()); verify(sender).send(message);
    }

    @Test
    void deveEncaminharPropostaParaHotmailSemRestringirODominio() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mensagem);
        EmailService service = new EmailService(mailSender, "contato@climbe.com.br", "Climbe", "");

        assertTrue(service.enviarEmailComBotao("cliente@hotmail.com", "Documento para análise", "Proposta",
                "Olá! Sua proposta está disponível para revisão.", "Revisar documento", "https://www.crmclimbe.com.br/revisao/token-teste", "Climbe"));

        assertEquals("cliente@hotmail.com", ((InternetAddress) mensagem.getAllRecipients()[0]).getAddress());
        mensagem.saveChanges();
        assertTrue(mensagem.isMimeType("multipart/related"));
        Multipart conteudo = (Multipart) mensagem.getContent();
        String html = conteudo.getBodyPart(0).getContent().toString();
        assertTrue(html.contains("https://www.crmclimbe.com.br/revisao/token-teste"));
        assertTrue(html.contains("src=\"cid:climbe-logo\""));
        assertTrue(conteudo.getBodyPart(1).isMimeType("image/png"));
        assertEquals(Part.INLINE, conteudo.getBodyPart(1).getDisposition());
        assertEquals("<climbe-logo>", conteudo.getBodyPart(1).getHeader("Content-ID")[0]);
        try (var logo = new ClassPathResource("mail/assets/climbe-logo.png").getInputStream();
             var incorporada = conteudo.getBodyPart(1).getInputStream()) {
            assertArrayEquals(logo.readAllBytes(), incorporada.readAllBytes());
        }
        verify(mailSender).send(mensagem);
    }

    @Test
    void deveInformarFalhaQuandoOServidorSmtpRecusarOEnvio() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mensagem);
        doThrow(new MailSendException("550 5.7.1 envio recusado")).when(mailSender).send(mensagem);
        EmailService service = new EmailService(mailSender, "contato@climbe.com.br", "Climbe", "");

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
                "contato@climbe.com.br"
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
