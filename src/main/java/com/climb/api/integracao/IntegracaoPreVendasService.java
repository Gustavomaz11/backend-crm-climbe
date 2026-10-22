package com.climb.api.integracao;

import com.climb.api.model.PipelineVendasFunil;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.model.dto.PipelineNegocioResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.PipelineVendasEtapaRepository;
import com.climb.api.repository.PipelineVendasFunilRepository;
import com.climb.api.service.PipelineVendasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class IntegracaoPreVendasService {
    private final PipelineVendasFunilRepository funis;
    private final PipelineVendasEtapaRepository etapas;
    private final PipelineVendasService negocios;
    private final IntegracaoEscopo escopo;

    @Transactional
    public PipelineNegocioResponseDTO criar(IntegracaoLeadRequest dto) {
        var funil = funis.findByCodigoAndAtivoTrue("PRE_VENDAS").filter(PipelineVendasFunil::isPreVendas)
                .orElseThrow(() -> IntegracaoService.erro(HttpStatus.CONFLICT,
                        "Ative o funil Pré-vendas · Todas as estratégias para receber leads pela integração."));
        var etapa = etapas.findByFunilIdFunilAndCodigoAndAtivoTrue(funil.getIdFunil(), "LISTA_LEADS")
                .filter(e -> e.getResultado() == PipelineVendasResultado.ABERTO)
                .orElseThrow(() -> IntegracaoService.erro(HttpStatus.CONFLICT,
                        "Configure a etapa Lista de leads como ativa e aberta no funil de Pré-vendas."));
        var empresa = escopo.empresa(dto.empresaId());
        var dados = new PipelineNegocioRequestDTO(funil.getIdFunil(), empresa.getIdEmpresa(), empresa.getNomeFantasia(),
                dto.nomeContato(), dto.telefone(), dto.email(), dto.responsavelId(), etapa.getIdEtapa(), null,
                dto.origemNegocio(), dto.estrategiaComercial(), null, null, dto.observacoes());
        return negocios.criar(escopo.ator(), dados);
    }
}
