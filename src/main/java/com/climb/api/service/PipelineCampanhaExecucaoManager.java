package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.PipelineExecucaoStatus;
import com.climb.api.model.enums.PipelineHistoricoTipo;
import com.climb.api.repository.PipelineCampanhaExecucaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PipelineCampanhaExecucaoManager {
    private final PipelineCampanhaExecucaoRepository repository;
    private final PipelineHistoricoService historicoService;

    public PipelineCampanhaExecucaoManager(PipelineCampanhaExecucaoRepository repository,
                                           PipelineHistoricoService historicoService) {
        this.repository = repository;
        this.historicoService = historicoService;
    }

    @Transactional
    public void sincronizar(PipelineCampanha campanha) {
        List<PipelineCampanhaExecucao> existentes = repository.findByCampanhaIdCampanha(campanha.getIdCampanha());
        List<PipelineCampanhaExecucao> alteradas = new ArrayList<>();
        if (!Boolean.TRUE.equals(campanha.getAtivo())) {
            pausar(existentes, alteradas);
            salvarEmLote(alteradas);
            return;
        }
        List<Usuario> participantes = campanha.getParticipantes().stream()
                .sorted(Comparator.comparing(Usuario::getId)).toList();
        Set<Long> leadsAtuais = campanha.getLeads().stream().map(PipelineVendasNegocio::getIdNegocio)
                .collect(Collectors.toSet());
        Map<String, PipelineCampanhaExecucao> execucoesPorLead = existentes.stream()
                .collect(Collectors.toMap(
                        execucao -> execucao.getNegocio().getIdNegocio() + ":" + execucao.getEtapaFunilCodigo(),
                        execucao -> execucao));
        pausarRemovidos(existentes, leadsAtuais, alteradas);
        if (participantes.isEmpty()) { salvarEmLote(alteradas); return; }
        int indice = 0;
        for (PipelineVendasNegocio lead : campanha.getLeads()) {
            if (lead.getResultado() != com.climb.api.model.enums.PipelineVendasResultado.ABERTO) continue;
            String codigo = escopo(campanha, lead);
            String chave = lead.getIdNegocio() + ":";
            PipelineCampanhaExecucao existente = lead.getEtapa() == null ? null : execucoesPorLead.get(chave + lead.getEtapa().getCodigo());
            PipelineCampanhaExecucao legado = execucoesPorLead.get(chave);
            if (legado != null && (legado.getStatus() == PipelineExecucaoStatus.ATIVA || legado.getStatus() == PipelineExecucaoStatus.PAUSADA)) existente = legado;
            if (existente == null && codigo != null) existente = execucoesPorLead.get(chave + codigo);
            if (codigo == null && existente == null) continue;
            ativarOuCriar(campanha, lead, escolherParticipante(lead, participantes, indice++), existente, alteradas);
        }
        salvarEmLote(alteradas);
    }

    private void ativarOuCriar(PipelineCampanha campanha,
                               PipelineVendasNegocio lead,
                               Usuario participante,
                               PipelineCampanhaExecucao existente,
                               List<PipelineCampanhaExecucao> alteradas) {
        if (existente != null) {
            PipelineCampanhaExecucao execucao = existente;
            if (execucao.getStatus() == PipelineExecucaoStatus.PAUSADA) {
                execucao.setStatus(PipelineExecucaoStatus.ATIVA);
                execucao.setProximaExecucaoEm(LocalDateTime.now());
                execucao.setParticipante(participante);
                alteradas.add(execucao);
            }
            return;
        }
        PipelineCampanhaExecucao execucao = new PipelineCampanhaExecucao();
        execucao.setCampanha(campanha);
        execucao.setVersao(campanha.getVersao());
        execucao.setEtapaFunilCodigo(escopo(campanha, lead));
        execucao.setNegocio(lead);
        execucao.setParticipante(participante);
        execucao.setStatus(PipelineExecucaoStatus.ATIVA);
        execucao.setOrdemAtual(0);
        execucao.setProximaExecucaoEm(LocalDateTime.now());
        alteradas.add(execucao);
        historicoService.registrar(lead, campanha.getCriadoPor().getId(), PipelineHistoricoTipo.CADENCIA_INICIADA,
                "Lead incluído na campanha " + campanha.getNome());
    }

    private Usuario escolherParticipante(PipelineVendasNegocio lead, List<Usuario> participantes, int indice) {
        return participantes.stream().filter(usuario -> Objects.equals(usuario.getId(), lead.getResponsavel().getId()))
                .findFirst().orElse(participantes.get(indice % participantes.size()));
    }

    private void pausarRemovidos(List<PipelineCampanhaExecucao> execucoes,
                                 Set<Long> leadsAtuais,
                                 List<PipelineCampanhaExecucao> alteradas) {
        pausar(execucoes.stream()
                .filter(execucao -> !leadsAtuais.contains(execucao.getNegocio().getIdNegocio()))
                .toList(), alteradas);
    }

    private void pausar(List<PipelineCampanhaExecucao> execucoes, List<PipelineCampanhaExecucao> alteradas) {
        execucoes.stream().filter(execucao -> execucao.getStatus() == PipelineExecucaoStatus.ATIVA).forEach(execucao -> {
            execucao.setStatus(PipelineExecucaoStatus.PAUSADA);
            execucao.setProximaExecucaoEm(null);
            alteradas.add(execucao);
        });
    }

    private void salvarEmLote(List<PipelineCampanhaExecucao> alteradas) {
        if (!alteradas.isEmpty()) repository.saveAll(alteradas);
    }
    private String escopo(PipelineCampanha campanha, PipelineVendasNegocio lead) {
        var atuais = campanha.getEtapas().stream().filter(e -> e.getVersao().equals(campanha.getVersao())).toList();
        if (atuais.stream().anyMatch(e -> e.getEtapaFunilCodigo().isEmpty())) return "";
        if (lead.getFunil() == null || !lead.getFunil().isPreVendas() || lead.getCampanhaOrigem() == null
                || !lead.getCampanhaOrigem().getIdCampanha().equals(campanha.getIdCampanha())) return null;
        return atuais.stream().anyMatch(e -> e.getEtapaFunilCodigo().equals(lead.getEtapa().getCodigo())) ? lead.getEtapa().getCodigo() : null;
    }
}
