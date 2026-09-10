package com.climb.api.service;

import com.climb.api.model.PipelineCampanha;
import com.climb.api.model.PipelineCampanhaExecucao;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.Usuario;
import com.climb.api.repository.PipelineCampanhaExecucaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineCampanhaExecucaoManagerTest {

    @Test
    void deveSincronizarTodosOsLeadsSemConsultaIndividual() {
        PipelineCampanhaExecucaoRepository repository = mock(PipelineCampanhaExecucaoRepository.class);
        PipelineHistoricoService historicoService = mock(PipelineHistoricoService.class);
        PipelineCampanhaExecucaoManager manager = new PipelineCampanhaExecucaoManager(repository, historicoService);
        Usuario responsavel = usuario(3L);
        PipelineCampanha campanha = new PipelineCampanha();
        campanha.getEtapas().add(new com.climb.api.model.PipelineCadenciaEtapa());
        campanha.setIdCampanha(5L);
        campanha.setNome("Campanha");
        campanha.setAtivo(true);
        campanha.setCriadoPor(responsavel);
        campanha.setParticipantes(Set.of(responsavel));
        campanha.setLeads(Set.of(negocio(10L, responsavel), negocio(11L, responsavel)));
        when(repository.findByCampanhaIdCampanha(5L)).thenReturn(List.of());

        manager.sincronizar(campanha);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PipelineCampanhaExecucao>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        verify(repository, never()).findByCampanhaIdCampanhaAndNegocioIdNegocio(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void retomaVersaoPausadaMesmoQuandoFluxoFoiRemovidoDoPlanoAtual() {
        var repository = mock(PipelineCampanhaExecucaoRepository.class);
        var manager = new PipelineCampanhaExecucaoManager(repository, mock(PipelineHistoricoService.class));
        var responsavel = usuario(3L);
        var campanha = new PipelineCampanha(); campanha.setIdCampanha(5L); campanha.setAtivo(true); campanha.setVersao(2);
        campanha.setParticipantes(Set.of(responsavel));
        var lead = negocio(10L, responsavel);
        var funil = new com.climb.api.model.PipelineVendasFunil(); funil.setTipo("PRE_VENDAS"); lead.setFunil(funil);
        var etapa = new com.climb.api.model.PipelineVendasEtapa(); etapa.setCodigo("TENTATIVA_CONTATO"); lead.setEtapa(etapa);
        lead.setCampanhaOrigem(campanha); campanha.setLeads(Set.of(lead));
        var anterior = new PipelineCampanhaExecucao(); anterior.setCampanha(campanha); anterior.setNegocio(lead);
        anterior.setEtapaFunilCodigo(etapa.getCodigo()); anterior.setVersao(1);
        anterior.setStatus(com.climb.api.model.enums.PipelineExecucaoStatus.PAUSADA);
        when(repository.findByCampanhaIdCampanha(5L)).thenReturn(List.of(anterior));
        manager.sincronizar(campanha);
        assertEquals(com.climb.api.model.enums.PipelineExecucaoStatus.ATIVA, anterior.getStatus());
        assertEquals(1, anterior.getVersao());
        verify(repository).saveAll(List.of(anterior));
    }

    private PipelineVendasNegocio negocio(Long id, Usuario responsavel) {
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(id);
        negocio.setResponsavel(responsavel);
        return negocio;
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }
}
