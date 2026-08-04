package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.PipelineTarefaNotificacaoTipo;
import com.climb.api.model.enums.PipelineTarefaPrioridade;
import com.climb.api.model.enums.PipelineTarefaStatus;
import com.climb.api.repository.CargoRepository;
import com.climb.api.repository.PipelineTarefaNotificacaoRepository;
import com.climb.api.repository.PipelineVendasTarefaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineTarefaReminderServiceTest {

    @Mock private PipelineVendasTarefaRepository tarefaRepository;
    @Mock private PipelineTarefaNotificacaoRepository notificacaoRepository;
    @Mock private CargoRepository cargoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PipelineTarefaReminderEmail reminderEmail;

    private PipelineTarefaReminderService service;

    @BeforeEach
    void setUp() {
        PipelineTarefaReminderLedger ledger = new PipelineTarefaReminderLedger(
                notificacaoRepository, "America/Sao_Paulo");
        service = new PipelineTarefaReminderService(
                tarefaRepository, ledger, cargoRepository, usuarioRepository,
                reminderEmail, "America/Sao_Paulo"
        );
    }

    @Test
    void deveAgruparTodasAsTarefasEmUmResumoSemanalPorResponsavel() {
        LocalDate hoje = LocalDate.of(2026, 8, 3);
        Usuario responsavel = usuario(2L, "Marcio", "marcio@climbe.com.br", null);
        List<PipelineVendasTarefa> tarefas = List.of(
                tarefa(1L, responsavel, hoje.minusDays(1)),
                tarefa(2L, responsavel, hoje.plusDays(5))
        );
        when(tarefaRepository.findAbertasComPrazoAte(hoje.plusDays(5))).thenReturn(tarefas);
        when(notificacaoRepository.findChavesExistentes(anyCollection())).thenReturn(List.of());
        when(reminderEmail.enviarResumoSemanal(responsavel, tarefas, hoje)).thenReturn(true);

        service.processarResumoSemanal(hoje);

        verify(reminderEmail).enviarResumoSemanal(responsavel, tarefas, hoje);
        ArgumentCaptor<PipelineTarefaNotificacao> captor = ArgumentCaptor.forClass(PipelineTarefaNotificacao.class);
        verify(notificacaoRepository).save(captor.capture());
        assertEquals(PipelineTarefaNotificacaoTipo.RESUMO_SEMANAL, captor.getValue().getTipo());
        assertEquals("2026-W32", captor.getValue().getReferencia());
    }

    @Test
    void deveEnviarSomenteOMarcoPendenteDeSeteDias() {
        LocalDate hoje = LocalDate.of(2026, 8, 3);
        Usuario responsavel = usuario(2L, "Marcio", "marcio@climbe.com.br", null);
        PipelineVendasTarefa tarefa = tarefa(1L, responsavel, hoje.minusDays(7));
        String prazo = tarefa.getPrazo().toString();
        Set<String> jaEnviadas = Set.of(
                "ATRASO_1_DIA:1:2:" + prazo,
                "ATRASO_3_DIAS:1:2:" + prazo
        );
        when(tarefaRepository.findAbertasComPrazoAte(hoje.minusDays(1))).thenReturn(List.of(tarefa));
        when(notificacaoRepository.findChavesExistentes(anyCollection())).thenAnswer(invocation -> {
            Collection<String> consultadas = invocation.getArgument(0);
            return consultadas.stream().filter(jaEnviadas::contains).toList();
        });
        when(reminderEmail.enviarLembreteAtrasos(responsavel, List.of(tarefa), hoje)).thenReturn(true);

        service.processarAlertasDeAtraso(hoje);

        ArgumentCaptor<List<PipelineTarefaNotificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(PipelineTarefaNotificacaoTipo.ATRASO_7_DIAS, captor.getValue().getFirst().getTipo());
    }

    @Test
    void deveEscalarAoCargoImediatamenteSuperiorDepoisDeSeteDias() {
        LocalDate hoje = LocalDate.of(2026, 8, 3);
        Cargo cargoResponsavel = cargo(20L, 10L);
        Usuario responsavel = usuario(2L, "Analista", "analista@climbe.com.br", cargoResponsavel);
        Usuario superior = usuario(3L, "Diretor", "diretor@climbe.com.br", cargo(10L, null));
        PipelineVendasTarefa tarefa = tarefa(1L, responsavel, hoje.minusDays(8));
        String prazo = tarefa.getPrazo().toString();
        Set<String> jaEnviadas = Set.of(
                "ATRASO_1_DIA:1:2:" + prazo,
                "ATRASO_3_DIAS:1:2:" + prazo,
                "ATRASO_7_DIAS:1:2:" + prazo
        );
        when(tarefaRepository.findAbertasComPrazoAte(hoje.minusDays(1))).thenReturn(List.of(tarefa));
        when(notificacaoRepository.findChavesExistentes(anyCollection())).thenAnswer(invocation -> {
            Collection<String> consultadas = invocation.getArgument(0);
            return consultadas.stream().filter(jaEnviadas::contains).toList();
        });
        when(cargoRepository.findByIdAndAtivoTrue(10L)).thenReturn(Optional.of(superior.getCargo()));
        when(usuarioRepository.findAllBySituacaoAndCargo_IdOrderByNomeCompletoAsc("ATIVO", 10L))
                .thenReturn(List.of(superior));
        when(reminderEmail.enviarEscalacao(superior, List.of(tarefa), hoje)).thenReturn(true);

        service.processarAlertasDeAtraso(hoje);

        verify(reminderEmail).enviarEscalacao(superior, List.of(tarefa), hoje);
        ArgumentCaptor<List<PipelineTarefaNotificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoRepository).saveAll(captor.capture());
        assertEquals(PipelineTarefaNotificacaoTipo.ESCALACAO_SUPERIOR, captor.getValue().getFirst().getTipo());
        assertEquals(superior, captor.getValue().getFirst().getDestinatario());
    }

    private Usuario usuario(Long id, String nome, String email, Cargo cargo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
        usuario.setEmail(email);
        usuario.setCargo(cargo);
        usuario.setSituacao("ATIVO");
        return usuario;
    }

    private Cargo cargo(Long id, Long superiorId) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setAtivo(true);
        cargo.setCargoSuperiorId(superiorId);
        return cargo;
    }

    private PipelineVendasTarefa tarefa(Long id, Usuario responsavel, LocalDate prazo) {
        PipelineVendasFunil funil = new PipelineVendasFunil();
        funil.setIdFunil(5L);
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(10L + id);
        negocio.setFunil(funil);
        negocio.setNomeEmpresa("Empresa " + id);
        negocio.setNomeContato("Cliente " + id);
        negocio.setServicoInteresse("BPO");
        PipelineVendasTarefa tarefa = new PipelineVendasTarefa();
        tarefa.setIdTarefa(id);
        tarefa.setNegocio(negocio);
        tarefa.setTitulo("Tarefa " + id);
        tarefa.setResponsavel(responsavel);
        tarefa.setPrazo(prazo);
        tarefa.setPrioridade(PipelineTarefaPrioridade.MEDIA);
        tarefa.setStatus(PipelineTarefaStatus.PENDENTE);
        tarefa.setTipo("Follow-up");
        return tarefa;
    }
}
