package com.climb.api.service;

import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class PipelineTarefaReminderEmail {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmailService emailService;
    private final String frontendUrl;

    public PipelineTarefaReminderEmail(
            EmailService emailService,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public boolean enviarResumoSemanal(Usuario destinatario,
                                       List<PipelineVendasTarefa> tarefas,
                                       LocalDate hoje) {
        return enviar(
                destinatario,
                "Resumo semanal de prazos comerciais",
                "Tarefas comerciais que precisam de atenção",
                "Estas tarefas estão vencidas ou vencem nos próximos 5 dias.",
                tarefas,
                hoje,
                false
        );
    }

    public boolean enviarLembreteAtrasos(Usuario destinatario,
                                         List<PipelineVendasTarefa> tarefas,
                                         LocalDate hoje) {
        return enviar(
                destinatario,
                "Lembrete de tarefas comerciais atrasadas",
                "Existem tarefas com prazo vencido",
                "Este aviso corresponde aos marcos de 1, 3 ou 7 dias após o vencimento.",
                tarefas,
                hoje,
                false
        );
    }

    public boolean enviarEscalacao(Usuario superior,
                                    List<PipelineVendasTarefa> tarefas,
                                    LocalDate hoje) {
        return enviar(
                superior,
                "Escalonamento de tarefas comerciais atrasadas",
                "Tarefas da equipe atrasadas há mais de 7 dias",
                "Você está recebendo este aviso por ser o superior direto dos responsáveis abaixo.",
                tarefas,
                hoje,
                true
        );
    }

    private boolean enviar(Usuario destinatario,
                           String assunto,
                           String titulo,
                           String introducao,
                           List<PipelineVendasTarefa> tarefas,
                           LocalDate hoje,
                           boolean mostrarResponsavel) {
        String saudacao = "<p style=\"margin:0 0 8px;color:#475569;font-size:15px;line-height:24px\">Olá, "
                + escape(destinatario.getNomeCompleto()) + ".</p>";
        String explicacao = "<p style=\"margin:0 0 22px;color:#475569;font-size:14px;line-height:22px\">"
                + escape(introducao) + "</p>";
        String corpo = saudacao + explicacao + tarefas.stream()
                .map(tarefa -> card(tarefa, hoje, mostrarResponsavel))
                .reduce("", String::concat);
        return emailService.enviarEmailComConteudoHtml(
                destinatario.getEmail(),
                assunto,
                titulo,
                corpo,
                "Os avisos deixam de ser enviados quando a tarefa é concluída ou cancelada."
        );
    }

    private String card(PipelineVendasTarefa tarefa, LocalDate hoje, boolean mostrarResponsavel) {
        PipelineVendasNegocio negocio = tarefa.getNegocio();
        long diasAtraso = java.time.temporal.ChronoUnit.DAYS.between(tarefa.getPrazo(), hoje);
        String situacao = diasAtraso > 0
                ? "Atrasada há " + diasAtraso + (diasAtraso == 1 ? " dia" : " dias")
                : diasAtraso == 0 ? "Vence hoje" : "Vence em " + Math.abs(diasAtraso) + " dias";
        String responsavel = mostrarResponsavel
                ? linha("Responsável", tarefa.getResponsavel().getNomeCompleto())
                : "";
        return """
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:0 0 14px;border:1px solid #e2e8f0;border-radius:12px;background:#f8fafc">
                  <tr><td style="padding:18px">
                    <div style="margin-bottom:10px;color:#0f172a;font-size:15px;font-weight:700">%s</div>
                    %s%s%s%s
                    <div style="margin-top:14px"><a href="%s" style="display:inline-block;padding:10px 16px;border-radius:8px;background:#16858a;color:#ffffff;text-decoration:none;font-size:13px;font-weight:700">Abrir tarefa</a></div>
                  </td></tr>
                </table>
                """.formatted(
                escape(tarefa.getTitulo()),
                linha("Empresa", negocio.getNomeEmpresa()),
                linha("Serviço", negocio.getServicoInteresse()),
                linha("Cliente", negocio.getNomeContato()),
                responsavel + linha("Prazo", DATE_FORMAT.format(tarefa.getPrazo()) + " · " + situacao),
                escape(link(tarefa))
        );
    }

    private String linha(String rotulo, String valor) {
        String conteudo = valor == null || valor.isBlank() ? "Não informado" : valor;
        return "<div style=\"margin-top:4px;color:#64748b;font-size:13px;line-height:19px\"><strong style=\"color:#334155\">"
                + escape(rotulo) + ":</strong> " + escape(conteudo) + "</div>";
    }

    private String link(PipelineVendasTarefa tarefa) {
        return frontendUrl + (tarefa.getNegocio().getFunil().isPreVendas() ? "/pipeline-pre-vendas?funilId=" : "/pipeline-vendas?funilId=") + tarefa.getNegocio().getFunil().getIdFunil()
                + "&negocioId=" + tarefa.getNegocio().getIdNegocio()
                + "&tarefaId=" + tarefa.getIdTarefa();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
