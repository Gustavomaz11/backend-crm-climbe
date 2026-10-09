package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KanbanPrazoAlertaService {
    private static final Logger log = LoggerFactory.getLogger(KanbanPrazoAlertaService.class);
    private final PipelineVendasTarefaRepository comerciais;
    private final ContratoKanbanTaskRepository contratos;
    private final UsuarioRepository usuarios;
    private final KanbanPrazoEnvioService envio;
    private final String frontend;

    public KanbanPrazoAlertaService(PipelineVendasTarefaRepository comerciais, ContratoKanbanTaskRepository contratos,
            UsuarioRepository usuarios, KanbanPrazoEnvioService envio,
            @Value("${app.frontend-url:http://localhost:5173}") String frontend) {
        this.comerciais = comerciais; this.contratos = contratos; this.usuarios = usuarios;
        this.envio = envio; this.frontend = frontend.replaceAll("/+$", "");
    }

    @Scheduled(cron = "${app.kanban.prazos.cron:0 0 * * * *}", zone = "America/Sao_Paulo")
    public void verificarPrazos() { processar(TarefaPrazoPolicy.hoje()); }

    void processar(LocalDate hoje) {
        Map<Long, List<KanbanPrazoAtividade>> porDestinatario = new TreeMap<>();
        var diretores = usuarios.buscarDiretoresComerciaisAtivos();
        comerciais.findAbertasComPrazoAte(hoje.plusDays(3)).forEach(t -> {
            Integer marco = marco(t.getPrazo(), hoje);
            if (marco == null) return;
            var atividade = comercial(t, marco);
            diretores.forEach(d -> adicionar(porDestinatario, d.getId(), atividade));
        });
        contratos.buscarAbertasComPrazoAte(hoje.plusDays(3)).forEach(t -> {
            Integer marco = marco(t.getDataFim(), hoje);
            Usuario lider = t.getContrato().getResponsavel();
            if (marco != null && lider != null) adicionar(porDestinatario, lider.getId(), contrato(t, marco));
        });
        porDestinatario.forEach((id, lista) -> {
            try { envio.enviar(id, lista, hoje); }
            catch (RuntimeException e) { log.error("Falha nos alertas de prazo do kanban para usuário {}", id, e); }
        });
    }

    static Integer marco(LocalDate prazo, LocalDate hoje) {
        if (prazo == null) return null;
        long dias = ChronoUnit.DAYS.between(hoje, prazo);
        if (dias == 3 || dias == 1 || dias == 0) return (int) dias;
        // Recupera o primeiro aviso de atraso que não foi enviado durante uma indisponibilidade.
        return dias < 0 ? -1 : null;
    }

    private KanbanPrazoAtividade comercial(PipelineVendasTarefa tarefa, int marco) {
        var negocio = tarefa.getNegocio(); var funil = negocio.getFunil();
        String link = frontend + (funil.isPreVendas() ? "/pipeline-pre-vendas" : "/pipeline-vendas")
                + "?funilId=" + funil.getIdFunil() + "&negocioId=" + negocio.getIdNegocio() + "&tarefaId=" + tarefa.getIdTarefa();
        return new KanbanPrazoAtividade(TarefaTipo.COMERCIAL, tarefa.getIdTarefa(), tarefa.getTitulo(), negocio.getNomeEmpresa(),
                String.join(", ", negocio.getServicosInteresse()), nomes(tarefa.getResponsaveisEfetivos()), tarefa.getPrazo(), marco, link);
    }
    private KanbanPrazoAtividade contrato(ContratoKanbanTask tarefa, int marco) {
        var contrato = tarefa.getContrato(); var proposta = contrato.getProposta();
        var servico = contrato.getServico() != null ? contrato.getServico() : proposta == null ? null : proposta.getServico();
        String servicos = proposta != null && !proposta.getServicos().isEmpty()
                ? proposta.getServicos().stream().map(s -> s.getServico().name().replace('_', ' ')).collect(Collectors.joining(", "))
                : servico == null ? null : servico.name().replace('_', ' ');
        return new KanbanPrazoAtividade(TarefaTipo.CONTRATO, tarefa.getIdTask(), tarefa.getTitulo(), contrato.getEmpresaNomeFantasia(),
                servicos, nomes(tarefa.getResponsaveisEfetivos()), tarefa.getDataFim(), marco,
                frontend + "/contratos/kanban?contrato=" + contrato.getIdContrato() + "&tarefaId=" + tarefa.getIdTask());
    }
    private String nomes(Collection<Usuario> pessoas) {
        return pessoas.stream().map(Usuario::getNomeCompleto).collect(Collectors.joining(", "));
    }
    private void adicionar(Map<Long, List<KanbanPrazoAtividade>> grupos, Long id, KanbanPrazoAtividade atividade) {
        grupos.computeIfAbsent(id, key -> new ArrayList<>()).add(atividade);
    }
}
