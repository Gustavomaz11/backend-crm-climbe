package com.climb.api.service;

import com.climb.api.model.PipelineCadenciaEtapa;
import com.climb.api.model.PipelineCampanha;
import com.climb.api.model.dto.PipelineCadenciaEtapaResponseDTO;
import com.climb.api.model.dto.PipelineCampanhaResponseDTO;
import com.climb.api.model.enums.PipelineExecucaoStatus;
import com.climb.api.repository.PipelineCampanhaExecucaoRepository;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
class PipelineCampanhaMapper {
    private final PipelineCampanhaExecucaoRepository execucaoRepository;

    PipelineCampanhaMapper(PipelineCampanhaExecucaoRepository execucaoRepository) {
        this.execucaoRepository = execucaoRepository;
    }

    PipelineCampanhaResponseDTO toResponse(PipelineCampanha campanha) {
        Long id = campanha.getIdCampanha();
        return new PipelineCampanhaResponseDTO(
                id, campanha.getNome(), campanha.getEstrategia(), campanha.getDescricao(),
                Boolean.TRUE.equals(campanha.getAtivo()),
                campanha.getLeads().stream().map(lead -> lead.getIdNegocio()).collect(Collectors.toSet()),
                campanha.getParticipantes().stream().map(usuario -> usuario.getId()).collect(Collectors.toSet()),
                campanha.getScripts().stream().map(script -> script.getIdScript()).collect(Collectors.toSet()),
                campanha.getDiasExecucao(),
                campanha.getEtapas().stream().filter(e -> e.getVersao().equals(campanha.getVersao())).map(this::etapa).toList(),
                execucaoRepository.countByCampanhaIdCampanhaAndStatus(id, PipelineExecucaoStatus.ATIVA),
                execucaoRepository.countByCampanhaIdCampanhaAndStatus(id, PipelineExecucaoStatus.CONCLUIDA),
                execucaoRepository.countByCampanhaIdCampanhaAndStatus(id, PipelineExecucaoStatus.SEM_RESPOSTA),
                campanha.getCriadoEm(), campanha.getAtualizadoEm()
        );
    }

    private PipelineCadenciaEtapaResponseDTO etapa(PipelineCadenciaEtapa etapa) {
        return new PipelineCadenciaEtapaResponseDTO(
                etapa.getIdEtapaCadencia(), etapa.getOrdem(), etapa.getTipo(), etapa.getTitulo(), etapa.getDescricao(),
                etapa.getTipoTarefa(), etapa.getPrioridade(), etapa.getDiasUteisEspera(), etapa.getPrazoDiasUteis(),
                etapa.getScript() == null ? null : etapa.getScript().getIdScript(),
                etapa.getScript() == null ? null : etapa.getScript().getNome(), etapa.getEtapaFunilCodigo()
        );
    }
}
