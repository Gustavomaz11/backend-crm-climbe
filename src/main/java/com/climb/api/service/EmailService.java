package com.climb.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String remetente;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:no-reply@climbe.com}") String remetente,
                        @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.frontendUrl = frontendUrl;
    }

    public void enviarEmailBoasVindas(String emailDestino, String nomeUsuario) {
        enviarEmail(
                emailDestino,
                "Bem-vindo ao sistema Climbe!",
                "Ola, " + nomeUsuario + "!\n\nSeu cadastro foi realizado com sucesso.\n"
                        + "As instrucoes de acesso foram enviadas para este e-mail."
        );
    }

    public void enviarAguardandoAprovacao(String emailDestino, String nomeUsuario) {
        enviarEmail(
                emailDestino,
                "Solicitação de acesso recebida - Climbe",
                "Olá, " + nomeOuPadrao(nomeUsuario) + "!\n\n"
                        + "Recebemos sua solicitação de acesso ao Climbe.\n"
                        + "Neste momento, seu cadastro está aguardando a aprovação de um administrador.\n\n"
                        + "Você receberá outro e-mail assim que seu acesso for aprovado."
        );
    }

    public void enviarAcessoAprovado(String emailDestino, String nomeUsuario) {
        enviarEmail(
                emailDestino,
                "Seu acesso ao Climbe foi aprovado",
                "Olá, " + nomeOuPadrao(nomeUsuario) + "!\n\n"
                        + "Seu acesso ao Climbe foi aprovado.\n"
                        + "Você já pode entrar no sistema usando a mesma forma de login utilizada no cadastro.\n\n"
                        + "Acesse: " + frontendUrl
        );
    }

    public void enviarEmail(String emailDestino, String assunto, String texto) {
        if (emailDestino == null || emailDestino.isBlank()) {
            log.warn("Envio de e-mail ignorado: destinatário vazio. Assunto: {}", assunto);
            return;
        }

        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(emailDestino);
            mensagem.setSubject(assunto);
            mensagem.setText(texto);
            mailSender.send(mensagem);
            log.info("E-mail enviado para {} com assunto '{}'", emailDestino, assunto);
        } catch (MailException e) {
            // Email delivery must not block the main business operation.
            log.error("Falha ao enviar e-mail para {} com assunto '{}': {}", emailDestino, assunto, e.getMessage(), e);
        }
    }

    private String nomeOuPadrao(String nomeUsuario) {
        return nomeUsuario == null || nomeUsuario.isBlank() ? "usuário" : nomeUsuario.trim();
    }
}
