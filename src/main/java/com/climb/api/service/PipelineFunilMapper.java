package com.climb.api.service;

import com.climb.api.model.PipelineVendasEtapa;
import com.climb.api.model.PipelineVendasFunil;
import com.climb.api.model.dto.PipelineEtapaConfiguracaoResponseDTO;
import com.climb.api.model.dto.PipelineFunilResponseDTO;
import com.climb.api.model.dto.PipelineFunilResumoResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PipelineFunilMapper {

    PipelineFunilResumoResponseDTO toResumo(PipelineVendasFunil funil) {
        return new PipelineFunilResumoResponseDTO(
                funil.getIdFunil(), funil.getNome(), funil.getDescricao(), funil.getEstrategia(),
                funil.getPosicao(), Boolean.TRUE.equals(funil.getAtivo()), funil.getTipo()
        );
    }

    PipelineFunilResponseDTO toResponse(PipelineVendasFunil funil) {
        return new PipelineFunilResponseDTO(
                funil.getIdFunil(), funil.getNome(), funil.getDescricao(), funil.getEstrategia(),
                funil.getPosicao(), Boolean.TRUE.equals(funil.getAtivo()),
                funil.getEtapas().stream().map(this::toEtapaResponse).toList(),
                funil.getCriadoEm(), funil.getAtualizadoEm(), funil.getTipo()
        );
    }

    private PipelineEtapaConfiguracaoResponseDTO toEtapaResponse(PipelineVendasEtapa etapa) {
        return new PipelineEtapaConfiguracaoResponseDTO(
                etapa.getIdEtapa(), etapa.getNome(), etapa.getObjetivo(), etapa.getCriteriosConclusao(),
                etapa.getPosicao(), etapa.getTempoMaximoPermanenciaDias(),
                etapa.getResultado() == PipelineVendasResultado.GANHO,
                etapa.getResultado() == PipelineVendasResultado.PERDIDO,
                camposObrigatorios(etapa), Boolean.TRUE.equals(etapa.getAtivo())
        );
    }

    private List<String> camposObrigatorios(PipelineVendasEtapa etapa) {
        return etapa.getCamposObrigatorios() == null ? List.of() : etapa.getCamposObrigatorios();
    }
}
