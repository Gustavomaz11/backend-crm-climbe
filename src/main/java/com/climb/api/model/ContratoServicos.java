package com.climb.api.model;

import com.climb.api.model.enums.ServicoComercial;
import java.util.LinkedHashSet;
import java.util.List;

public final class ContratoServicos {
    private ContratoServicos() {}

    public static String chave(ServicoComercial servico) { return servico == null ? "SEM_SERVICO" : servico.name(); }

    public static boolean encerrado(Contrato contrato, ServicoComercial servico) {
        return contrato.getEncerramentosPorServico().containsKey(chave(servico));
    }

    public static List<ServicoComercial> listar(Contrato contrato) {
        var servicos = new LinkedHashSet<ServicoComercial>();
        if (contrato.getProposta() != null) {
            contrato.getProposta().getServicos().forEach(item -> {
                if (item.getServico() != null) servicos.add(item.getServico());
            });
        }
        if (servicos.isEmpty() && contrato.getServico() != null) servicos.add(contrato.getServico());
        if (servicos.isEmpty() && contrato.getProposta() != null && contrato.getProposta().getServico() != null) {
            servicos.add(contrato.getProposta().getServico());
        }
        return List.copyOf(servicos);
    }

    public static ServicoComercial primeiro(Contrato contrato) {
        return listar(contrato).stream().findFirst().orElse(null);
    }

    public static List<ServicoComercial> listar(Proposta proposta) {
        var servicos = new LinkedHashSet<ServicoComercial>();
        proposta.getServicos().forEach(item -> {
            if (item.getServico() != null) servicos.add(item.getServico());
        });
        if (servicos.isEmpty() && proposta.getServico() != null) servicos.add(proposta.getServico());
        return List.copyOf(servicos);
    }

    public static Usuario responsavel(Contrato contrato, ServicoComercial servico) {
        return contrato.getResponsaveisPorServico().getOrDefault(servico, contrato.getResponsavel());
    }

    public static List<Usuario> responsaveis(Contrato contrato) {
        if (!contrato.getResponsaveisPorServico().isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(contrato.getResponsaveisPorServico().values()));
        }
        return contrato.getResponsavel() == null ? List.of() : List.of(contrato.getResponsavel());
    }

    public static Usuario responsavelDaTarefa(ContratoKanbanTask tarefa) {
        return responsavel(tarefa.getContrato(), daTarefa(tarefa));
    }

    public static ServicoComercial daRaia(ContratoKanbanRaia raia) {
        return raia.getServico() != null ? raia.getServico() : primeiro(raia.getContrato());
    }

    public static ServicoComercial daTarefa(ContratoKanbanTask tarefa) {
        return tarefa.getRaia() != null && tarefa.getRaia().getServico() != null
                ? tarefa.getRaia().getServico() : primeiro(tarefa.getContrato());
    }
}
