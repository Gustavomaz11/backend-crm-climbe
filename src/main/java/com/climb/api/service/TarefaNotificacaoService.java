package com.climb.api.service;

import com.climb.api.model.ContratoKanbanRaia;
import com.climb.api.model.ContratoKanbanTask;
import com.climb.api.model.PipelineVendasTarefa;
import com.climb.api.model.Usuario;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TarefaNotificacaoService {
    private final NotificacaoEntregaService entrega;
    public TarefaNotificacaoService(NotificacaoEntregaService entrega) { this.entrega = entrega; }

    public static String estado(ContratoKanbanRaia raia) {
        if (raia.isConcluiTarefas()) return "CONCLUIDA";
        String titulo = Normalizer.normalize(raia.getTitulo(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
        return titulo.equals("a fazer") ? "A_FAZER" : "EM_ANDAMENTO";
    }

    public void contratoCriada(ContratoKanbanTask tarefa) {
        notificarLider(tarefa, "TAREFA_CRIADA", "Tarefa criada", "foi criada");
        if ("CONCLUIDA".equals(estado(tarefa.getRaia()))) {
            notificarLider(tarefa, "TAREFA_CONCLUIDA", "Tarefa concluída", "foi concluída");
        }
    }

    public void contratoMovida(ContratoKanbanTask tarefa, String estadoAnterior) {
        String atual = estado(tarefa.getRaia());
        if (atual.equals(estadoAnterior)) return;
        if (atual.equals("A_FAZER")) notificarLider(tarefa, "TAREFA_A_FAZER", "Tarefa em À fazer", "foi colocada em À fazer");
        if (atual.equals("CONCLUIDA")) notificarLider(tarefa, "TAREFA_CONCLUIDA", "Tarefa concluída", "foi concluída");
    }

    public void atribuicoes(ContratoKanbanTask tarefa, Set<Long> anteriores) {
        String destino = ContratoKanbanEndereco.daTarefa(tarefa);
        notificarAtribuidos(tarefa.getResponsaveisEfetivos(), anteriores, tarefa.getTitulo(),
                tarefa.getContrato().getEmpresaNomeFantasia(), tarefa.getDataFim(), destino);
    }

    public void atribuicoes(PipelineVendasTarefa tarefa, Set<Long> anteriores) {
        var negocio = tarefa.getNegocio();
        String menu = "PRE_VENDAS".equals(negocio.getFunil().getTipo()) ? "/pipeline-pre-vendas" : "/pipeline-vendas";
        String destino = menu + "?funilId=" + negocio.getFunil().getIdFunil()
                + "&negocioId=" + negocio.getIdNegocio() + "&tarefaId=" + tarefa.getIdTarefa();
        notificarAtribuidos(tarefa.getResponsaveisEfetivos(), anteriores, tarefa.getTitulo(), negocio.getNomeEmpresa(), tarefa.getPrazo(), destino);
    }

    private void notificarAtribuidos(Collection<Usuario> usuarios, Set<Long> anteriores, String titulo,
                                     String contexto, java.time.LocalDate prazo, String destino) {
        usuarios.stream().filter(u -> !anteriores.contains(u.getId())).forEach(usuario ->
                entrega.notificar(usuario, "TAREFA_ATRIBUIDA", "Uma tarefa foi atribuída a você",
                        "Você foi atribuído como responsável pela tarefa \"" + titulo + "\".\n"
                                + "Contrato/negociação: " + contexto + ".\nPrazo: "
                                + (prazo == null ? "Não informado" : prazo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) + ".",
                        destino));
    }

    private void notificarLider(ContratoKanbanTask tarefa, String tipo, String assunto, String acao) {
        entrega.notificar(com.climb.api.model.ContratoServicos.responsavelDaTarefa(tarefa), tipo, assunto,
                "A tarefa \"" + tarefa.getTitulo() + "\" " + acao + ".\nEmpresa: "
                        + tarefa.getContrato().getEmpresaNomeFantasia() + ".\nResponsáveis: "
                        + tarefa.getResponsaveisEfetivos().stream().map(Usuario::getNomeCompleto).collect(Collectors.joining(", "))
                        + ".\nColuna: " + tarefa.getRaia().getTitulo() + ".",
                ContratoKanbanEndereco.daTarefa(tarefa));
    }
}
