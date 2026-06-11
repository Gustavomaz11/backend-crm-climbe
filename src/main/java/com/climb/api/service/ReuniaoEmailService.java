package com.climb.api.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.climb.api.model.Reuniao;

@Service
public class ReuniaoEmailService {

    private static final DateTimeFormatter DATA_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));
    private static final DateTimeFormatter HORA_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm", new Locale("pt", "BR"));

    private final EmailService emailService;

    public ReuniaoEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void enviarConfirmacaoCriacao(Reuniao reuniao,
                                         String linkMeet,
                                         String emailCriador,
                                         String nomeCriador,
                                         Set<String> emailsConvidados) {
        if (!StringUtils.hasText(linkMeet)) {
            return;
        }

        String assunto = "Sua próxima reunião foi agendada com sucesso!";
        String corpoCriador = montarCorpo(
                StringUtils.hasText(nomeCriador) ? nomeCriador : "usuario",
                reuniao,
                linkMeet,
                true
        );

        emailService.enviarEmail(emailCriador, assunto, corpoCriador);

        Set<String> destinatarios = new LinkedHashSet<>();
        if (emailsConvidados != null) {
            for (String email : emailsConvidados) {
                if (StringUtils.hasText(email) && !email.equalsIgnoreCase(emailCriador)) {
                    destinatarios.add(email.trim());
                }
            }
        }

        String corpoConvidado = montarCorpo("participante", reuniao, linkMeet, false);
        for (String destinatario : destinatarios) {
            emailService.enviarEmail(destinatario, assunto, corpoConvidado);
        }
    }

    private String montarCorpo(String destinatario, Reuniao reuniao, String linkMeet, boolean criadoPorVoce) {
        String inicio = reuniao.getData() != null && reuniao.getHora() != null
                ? reuniao.getData().atTime(reuniao.getHora()).atZone(ZoneId.of("America/Fortaleza")).format(DATA_FORMATTER) + " as " + reuniao.getHora().format(HORA_FORMATTER)
                : "data e horário a confirmar";

        String confirmacao = criadoPorVoce
                ? "Sua próxima reunião foi agendada com sucesso."
                : "Você foi convidado para uma reunião agendada no sistema.";

        return "Ola, " + destinatario + "!\n\n"
                + confirmacao + "\n\n"
                + "Titulo: " + valorOuPadrao(reuniao.getTitulo(), "Reunião Climb") + "\n"
                + "Empresa: " + (reuniao.getEmpresa() != null ? valorOuPadrao(reuniao.getEmpresa().getNomeFantasia(), reuniao.getEmpresa().getRazaoSocial()) : "Nao informada") + "\n"
                + "Quando: " + inicio + "\n"
                + "Link do Google Meet: " + linkMeet + "\n\n"
                + "Se precisar, acesse o link acima no horário agendado.";
    }

    private String valorOuPadrao(String valor, String padrao) {
        return StringUtils.hasText(valor) ? valor : padrao;
    }
}
