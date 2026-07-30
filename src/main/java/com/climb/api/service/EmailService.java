package com.climb.api.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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
                "Olá, " + nomeOuPadrao(nomeUsuario) + "!\n\nSeu cadastro foi realizado com sucesso.\n"
                        + "As instruções de acesso foram enviadas para este e-mail."
        );
    }

    public void enviarAguardandoAprovacao(String emailDestino, String nomeUsuario) {
        enviarEmailComBotao(
                emailDestino,
                "Solicitação de acesso recebida - Climbe",
                "Solicitação recebida",
                "Olá, " + nomeOuPadrao(nomeUsuario) + "! Recebemos sua solicitação de acesso. "
                        + "O cadastro está aguardando a aprovação de um administrador e você receberá outro e-mail assim que for liberado.",
                null,
                null,
                "Nenhuma ação é necessária neste momento."
        );
    }

    public void enviarAcessoAprovado(String emailDestino, String nomeUsuario) {
        enviarEmailComBotao(
                emailDestino,
                "Seu acesso ao Climbe foi aprovado",
                "Acesso aprovado",
                "Olá, " + nomeOuPadrao(nomeUsuario) + "! Seu acesso ao Climbe foi aprovado. "
                        + "Entre usando a mesma forma de login utilizada no cadastro.",
                "Acessar o Climbe",
                frontendUrl,
                "Se você não solicitou este acesso, avise a equipe Climbe."
        );
    }

    public void enviarEmail(String emailDestino, String assunto, String texto) {
        String mensagemHtml = HtmlUtils.htmlEscape(texto == null ? "" : texto)
                .replace("\r\n", "<br>")
                .replace("\n", "<br>");
        enviarEmailHtml(emailDestino, assunto, montarTemplate(assunto, mensagemHtml, null, null,
                "Esta é uma mensagem automática da Climbe."));
    }

    public boolean enviarEmailComBotao(String emailDestino,
                                       String assunto,
                                       String titulo,
                                       String mensagem,
                                       String textoBotao,
                                       String urlBotao,
                                       String rodape) {
        String corpo = "<p style=\"margin:0;color:#475569;font-size:15px;line-height:24px\">"
                + HtmlUtils.htmlEscape(mensagem == null ? "" : mensagem) + "</p>";
        return enviarEmailHtml(emailDestino, assunto,
                montarTemplate(titulo, corpo, textoBotao, urlBotao, rodape));
    }

    public boolean enviarEmailHtml(String emailDestino, String assunto, String html) {
        if (emailDestino == null || emailDestino.isBlank()) {
            log.warn("Envio de e-mail ignorado: destinatário vazio. Assunto: {}", assunto);
            return false;
        }

        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(emailDestino.trim());
            helper.setSubject(assunto);
            helper.setText(html, true);
            mailSender.send(mensagem);
            log.info("E-mail HTML enviado para {} com assunto '{}'", emailDestino, assunto);
            return true;
        } catch (Exception e) {
            // A operação principal permanece disponível e o e-mail pode ser reenviado pela revisão.
            log.error("Falha ao enviar e-mail para {} com assunto '{}': {}", emailDestino, assunto, e.getMessage(), e);
            return false;
        }
    }

    private String montarTemplate(String titulo,
                                  String corpoHtml,
                                  String textoBotao,
                                  String urlBotao,
                                  String rodape) {
        String botao = textoBotao == null || urlBotao == null ? "" : """
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:28px 0 8px">
                  <tr><td style="border-radius:10px;background:#16858a">
                    <a href="%s" style="display:inline-block;padding:14px 24px;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700">%s</a>
                  </td></tr>
                </table>
                """.formatted(HtmlUtils.htmlEscape(urlBotao), HtmlUtils.htmlEscape(textoBotao));

        return """
                <!doctype html>
                <html lang="pt-BR"><body style="margin:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f1f5f9;padding:32px 12px">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden;box-shadow:0 12px 28px rgba(15,23,42,.08)">
                      <tr><td style="padding:24px 30px;background:#0f172a">
                        <span style="color:#ffffff;font-size:23px;font-weight:800;letter-spacing:-.5px">climb</span><span style="color:#4db6b2;font-size:23px;font-weight:800">▰</span>
                      </td></tr>
                      <tr><td style="padding:34px 30px 28px">
                        <div style="display:inline-block;margin-bottom:16px;padding:6px 10px;border-radius:999px;background:#e6f4f3;color:#13777b;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.8px">Climbe CRM</div>
                        <h1 style="margin:0 0 16px;color:#0f172a;font-size:24px;line-height:32px">%s</h1>
                        %s
                        %s
                      </td></tr>
                      <tr><td style="padding:20px 30px;border-top:1px solid #e2e8f0;background:#f8fafc;color:#94a3b8;font-size:12px;line-height:18px">%s<br>© Climbe</td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(
                HtmlUtils.htmlEscape(titulo == null ? "Climbe" : titulo),
                corpoHtml == null ? "" : corpoHtml,
                botao,
                HtmlUtils.htmlEscape(rodape == null ? "" : rodape)
        );
    }

    private String nomeOuPadrao(String nomeUsuario) {
        return nomeUsuario == null || nomeUsuario.isBlank() ? "usuário" : nomeUsuario.trim();
    }
}
