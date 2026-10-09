package com.climb.api.service;

import com.climb.api.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class KanbanPrazoEmailService {
    private final EmailService email;
    public KanbanPrazoEmailService(EmailService email) { this.email = email; }

    public boolean enviar(Usuario destinatario, List<KanbanPrazoAtividade> atividades, LocalDate hoje) {
        String corpo = "<p>Olá, " + escape(destinatario.getNomeCompleto())
                + ". Estas atividades precisam da sua atenção:</p>";
        corpo += atividades.stream().map(a -> card(a, hoje)).collect(java.util.stream.Collectors.joining());
        boolean atrasos = atividades.stream().anyMatch(a -> a.prazo().isBefore(hoje));
        return email.enviarEmailComConteudoHtml(destinatario.getEmail(),
                atrasos ? "Kanban: atividades atrasadas e prazos próximos" : "Kanban: prazos das atividades",
                "Acompanhamento dos prazos do kanban", corpo,
                "Avisos de 3 dias, 1 dia, vencimento e primeiro dia de atraso. Atividades concluídas não entram nos avisos.");
    }

    private String card(KanbanPrazoAtividade atividade, LocalDate hoje) {
        long dias = ChronoUnit.DAYS.between(hoje, atividade.prazo());
        String situacao = dias == 0 ? "Vence hoje" : dias > 0 ? "Vence em " + dias + (dias == 1 ? " dia" : " dias")
                : "Atrasada há " + -dias + (dias == -1 ? " dia" : " dias");
        return "<div style=\"margin:16px 0;padding:16px;border:1px solid #e2e8f0;border-radius:12px\"><h3>"
                + escape(atividade.titulo()) + "</h3>"
                + linha("Empresa", atividade.empresa()) + linha("Serviços", atividade.servicos())
                + linha("Responsáveis", atividade.responsaveis())
                + linha("Prazo", atividade.prazo().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " · " + situacao)
                + "<p><a href=\"" + escape(atividade.link()) + "\">Abrir atividade</a></p></div>";
    }
    private String linha(String label, String value) {
        return "<div><strong>" + label + ":</strong> " + escape(value == null || value.isBlank() ? "Não informado" : value) + "</div>";
    }
    private String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
}
