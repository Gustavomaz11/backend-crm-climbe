package com.climb.api.service;

import com.climb.api.model.PipelineVendasEtapa;
import com.climb.api.model.PipelineVendasMovimentacaoEtapa;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.repository.PipelineVendasMovimentacaoEtapaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PipelineMovimentacaoEtapaService {
    private final PipelineVendasMovimentacaoEtapaRepository repository;

    public PipelineMovimentacaoEtapaService(PipelineVendasMovimentacaoEtapaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void iniciar(PipelineVendasNegocio negocio, LocalDateTime entradaEm) {
        repository.save(novaMovimentacao(negocio, negocio.getEtapa(), entradaEm, null));
    }

    @Transactional
    public void mudar(PipelineVendasNegocio negocio,
                      PipelineVendasEtapa novaEtapa,
                      LocalDateTime momento,
                      boolean encerrada) {
        repository.findFirstByNegocioIdNegocioAndSaidaEmIsNullOrderByEntradaEmDesc(negocio.getIdNegocio())
                .ifPresent(atual -> {
                    atual.setSaidaEm(momento);
                    repository.save(atual);
                });
        repository.save(novaMovimentacao(negocio, novaEtapa, momento, encerrada ? momento : null));
    }

    private PipelineVendasMovimentacaoEtapa novaMovimentacao(PipelineVendasNegocio negocio,
                                                              PipelineVendasEtapa etapa,
                                                              LocalDateTime entrada,
                                                              LocalDateTime saida) {
        PipelineVendasMovimentacaoEtapa movimentacao = new PipelineVendasMovimentacaoEtapa();
        movimentacao.setNegocio(negocio);
        movimentacao.setEtapa(etapa);
        movimentacao.setEntradaEm(entrada);
        movimentacao.setSaidaEm(saida);
        return movimentacao;
    }
}
