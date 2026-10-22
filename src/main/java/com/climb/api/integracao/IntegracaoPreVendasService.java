package com.climb.api.integracao;

import com.climb.api.model.PipelineVendasFunil;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.model.dto.PipelineNegocioResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.PipelineVendasEtapaRepository;
import com.climb.api.repository.PipelineVendasFunilRepository;
import com.climb.api.repository.EmpresaRepository;
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
    private final EmpresaRepository empresas;

    @Transactional
    public PipelineNegocioResponseDTO criar(IntegracaoLeadRequest dto) {
        var funil = funis.findByCodigoAndAtivoTrue("PRE_VENDAS").filter(PipelineVendasFunil::isPreVendas)
                .orElseThrow(() -> IntegracaoService.erro(HttpStatus.CONFLICT,
                        "Ative o funil Pré-vendas · Todas as estratégias para receber leads pela integração."));
        var etapa = etapas.findByFunilIdFunilAndCodigoAndAtivoTrue(funil.getIdFunil(), "LISTA_LEADS")
                .filter(e -> e.getResultado() == PipelineVendasResultado.ABERTO)
                .orElseThrow(() -> IntegracaoService.erro(HttpStatus.CONFLICT,
                        "Configure a etapa Lista de leads como ativa e aberta no funil de Pré-vendas."));
        String cnpj = dto.cnpj().replaceAll("\\D", "");
        String formatado = "%s.%s.%s/%s-%s".formatted(cnpj.substring(0, 2), cnpj.substring(2, 5),
                cnpj.substring(5, 8), cnpj.substring(8, 12), cnpj.substring(12, 14));
        var empresa = empresas.findFirstByCnpjIn(java.util.List.of(cnpj, formatado));
        var dados = new PipelineNegocioRequestDTO(funil.getIdFunil(), empresa.map(e -> e.getIdEmpresa()).orElse(null), dto.nomeEmpresa(),
                dto.nomeContato(), dto.telefone(), dto.email(), 0L, etapa.getIdEtapa(), null,
                dto.origemNegocio(), dto.estrategiaComercial(), null, null, dto.observacoes(),
                null, empresa.isEmpty(), dto.cnpj());
        return negocios.criar(escopo.ator(), dados);
    }
}
