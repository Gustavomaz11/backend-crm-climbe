package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.PipelineExecucaoStatus;
import com.climb.api.model.enums.PipelineHistoricoTipo;
import com.climb.api.repository.PipelineCampanhaExecucaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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
        if (!Boolean.TRUE.equals(campanha.getAtivo())) {
            pausar(repository.findByCampanhaIdCampanha(campanha.getIdCampanha()));
            return;
        }
        List<Usuario> participantes = campanha.getParticipantes().stream()
                .sorted(Comparator.comparing(Usuario::getId)).toList();
        Set<Long> leadsAtuais = campanha.getLeads().stream().map(PipelineVendasNegocio::getIdNegocio)
                .collect(Collectors.toSet());
        pausarRemovidos(repository.findByCampanhaIdCampanha(campanha.getIdCampanha()), leadsAtuais);
        int indice = 0;
        for (PipelineVendasNegocio lead : campanha.getLeads()) {
            ativarOuCriar(campanha, lead, escolherParticipante(lead, participantes, indice++));
        }
    }

    private void ativarOuCriar(PipelineCampanha campanha, PipelineVendasNegocio lead, Usuario participante) {
        var existente = repository.findByCampanhaIdCampanhaAndNegocioIdNegocio(
                campanha.getIdCampanha(), lead.getIdNegocio());
        if (existente.isPresent()) {
            PipelineCampanhaExecucao execucao = existente.get();
            if (execucao.getStatus() == PipelineExecucaoStatus.PAUSADA) {
                execucao.setStatus(PipelineExecucaoStatus.ATIVA);
                execucao.setProximaExecucaoEm(LocalDateTime.now());
                execucao.setParticipante(participante);
                repository.save(execucao);
            }
            return;
        }
        PipelineCampanhaExecucao execucao = new PipelineCampanhaExecucao();
        execucao.setCampanha(campanha);
        execucao.setNegocio(lead);
        execucao.setParticipante(participante);
        execucao.setStatus(PipelineExecucaoStatus.ATIVA);
        execucao.setOrdemAtual(0);
        execucao.setProximaExecucaoEm(LocalDateTime.now());
        repository.save(execucao);
        historicoService.registrar(lead, campanha.getCriadoPor().getId(), PipelineHistoricoTipo.CADENCIA_INICIADA,
                "Lead incluído na campanha " + campanha.getNome());
    }

    private Usuario escolherParticipante(PipelineVendasNegocio lead, List<Usuario> participantes, int indice) {
        return participantes.stream().filter(usuario -> Objects.equals(usuario.getId(), lead.getResponsavel().getId()))
                .findFirst().orElse(participantes.get(indice % participantes.size()));
    }

    private void pausarRemovidos(List<PipelineCampanhaExecucao> execucoes, Set<Long> leadsAtuais) {
        pausar(execucoes.stream().filter(execucao -> !leadsAtuais.contains(execucao.getNegocio().getIdNegocio())).toList());
    }

    private void pausar(List<PipelineCampanhaExecucao> execucoes) {
        execucoes.stream().filter(execucao -> execucao.getStatus() == PipelineExecucaoStatus.ATIVA).forEach(execucao -> {
            execucao.setStatus(PipelineExecucaoStatus.PAUSADA);
            execucao.setProximaExecucaoEm(null);
            repository.save(execucao);
        });
    }
}
