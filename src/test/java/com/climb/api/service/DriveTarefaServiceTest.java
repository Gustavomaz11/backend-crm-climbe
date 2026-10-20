package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriveTarefaServiceTest {
    private final ContratoKanbanService tarefas = mock(ContratoKanbanService.class);
    private final EmpresaDriveServicoRepository pastas = mock(EmpresaDriveServicoRepository.class);
    private final GoogleDriveClient drive = mock(GoogleDriveClient.class);
    private final DriveOrganizacaoService organizacao = new DriveOrganizacaoService(null, null, null);
    private final DriveTarefaService service = new DriveTarefaService(tarefas, pastas, mock(EmpresaDriveRepository.class),
            organizacao, drive, mock(ArquivoValidationService.class));

    @BeforeEach void preparar() {
        var empresa = new Empresa(); empresa.setIdEmpresa(1L);
        var contrato = new Contrato(); contrato.setEmpresa(empresa); contrato.setServico(ServicoComercial.BPO);
        var tarefa = new ContratoKanbanTask(); tarefa.setContrato(contrato);
        when(tarefas.exigirTaskVisivel(anyLong(), eq(7L))).thenReturn(tarefa);
        var pasta = new EmpresaDriveServico(); pasta.setEmpresaId(1L); pasta.setServico(ServicoComercial.BPO); pasta.setPastaId("bpo");
        when(pastas.findByEmpresaIdAndServico(1L, ServicoComercial.BPO)).thenReturn(Optional.of(pasta));
    }

    @Test void tarefasDiferentesConsultamAMesmaPastaDoServicoELeemAlteracoesExternas() {
        when(drive.buscar("bpo")).thenReturn(pasta("bpo", "empresa"));
        when(drive.listar("bpo")).thenReturn(List.of()).thenReturn(List.of(pasta("nova", "bpo")));
        assertThat(service.listar(10L, 7L, ServicoComercial.BPO, null)).isEmpty();
        assertThat(service.listar(20L, 7L, ServicoComercial.BPO, null)).extracting(GoogleDriveClient.Arquivo::id).containsExactly("nova");
        verify(drive, times(2)).listar("bpo");
    }

    @Test void rejeitaArquivoDeOutraEmpresaOuOutroServicoMesmoConhecendoId() {
        when(drive.buscar("segredo")).thenReturn(pasta("segredo", "outra"));
        when(drive.buscar("outra")).thenReturn(pasta("outra", null));
        assertThatThrownBy(() -> service.baixar(10L, 7L, ServicoComercial.BPO, "segredo"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(drive, never()).baixar(any());
        assertThatThrownBy(() -> service.listar(10L, 7L, ServicoComercial.CFO, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test void exigeVisibilidadeDaTarefaAntesDeConsultarDrive() {
        when(tarefas.exigirTaskVisivel(10L, 7L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.listar(10L, 7L, ServicoComercial.BPO, null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(drive);
    }

    @Test void tarefaCfoNaoPodeAbrirPastaDeOutroServicoDoMesmoContrato() {
        var tarefa = tarefas.exigirTaskVisivel(10L, 7L);
        var proposta = new Proposta(); proposta.setServicos(List.of(
                new PropostaServico(ServicoComercial.BPO, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)));
        tarefa.getContrato().setProposta(proposta);
        var raia = new ContratoKanbanRaia(); raia.setContrato(tarefa.getContrato()); raia.setServico(ServicoComercial.CFO);
        tarefa.setRaia(raia);
        assertThat(service.servicos(10L, 7L).servicos()).extracting(DriveTarefaService.Servico::servico).containsExactly(ServicoComercial.CFO);
        assertThatThrownBy(() -> service.listar(10L, 7L, ServicoComercial.BPO, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verifyNoInteractions(drive);
    }

    @Test void criaSubpastasNaPastaRealDoServico() {
        when(drive.buscar("2026")).thenReturn(pasta("2026", "bpo")); when(drive.buscar("bpo")).thenReturn(pasta("bpo", "empresa"));
        service.criarPasta(20L, 7L, ServicoComercial.BPO, new DriveTarefaService.NovaPasta(" Balanços ", "2026"));
        verify(drive).criarPasta("2026", "Balanços", null);
    }

    private GoogleDriveClient.Arquivo pasta(String id, String pai) {
        return new GoogleDriveClient.Arquivo(id, id, GoogleDriveClient.PASTA, 0, pai == null ? List.of() : List.of(pai), false, null);
    }
}
