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
