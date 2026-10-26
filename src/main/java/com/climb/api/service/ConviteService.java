package com.climb.api.service;

import com.climb.api.model.dto.ConviteLoteRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;

@Service
public class ConviteService {
    public static final String ASSUNTO = "Bem-vindo ao CRM da Climbe!";
    public static final String MENSAGEM = """
            Olá, sócio!

            Seja bem-vindo ao CRM da Climbe, nosso espaço para conectar pessoas, organizar oportunidades e acompanhar os negócios.

            Clique no botão abaixo e entre com sua conta Google para solicitar acesso ao sistema. Após a aprovação do seu cadastro, você poderá começar a usar o CRM.

            Vamos construir novas oportunidades juntos!
            Equipe Climbe""";
    private final EmailNotificacaoFilaService fila;

    public ConviteService(EmailNotificacaoFilaService fila) {
        this.fila = fila;
    }

    @Transactional
    public Resultado convidar(ConviteLoteRequestDTO request) {
        var destinatarios = new LinkedHashSet<>(request.emails());
        for (String email : destinatarios) {
            fila.enfileirar(new NotificacaoEntregaService.EmailNotificacao(email, ASSUNTO, MENSAGEM, "/convite"));
        }
        return new Resultado(destinatarios.size(), request.emails().size() - destinatarios.size());
    }

    public record Resultado(int agendados, int duplicadosIgnorados) {}
}
