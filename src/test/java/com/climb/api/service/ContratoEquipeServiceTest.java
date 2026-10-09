package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContratoEquipeServiceTest {
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoEquipeRepository membros = mock(ContratoEquipeRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ContratoKanbanTaskRepository tarefas = mock(ContratoKanbanTaskRepository.class);
    private final ContratoNotificacaoService notificacoes = mock(ContratoNotificacaoService.class);
    private final RbacService rbac = mock(RbacService.class);
    private final List<ContratoEquipeMembro> fixos = new ArrayList<>();
    private final List<ContratoKanbanTask> criadas = new ArrayList<>();
    private ContratoEquipeService service;
    private Contrato contrato;
    private Usuario lider, ana, bia;

    @BeforeEach
    void setup() {
        service = new ContratoEquipeService(contratos, membros, usuarios, notificacoes, rbac, tarefas);
        lider = usuario(1L); ana = usuario(2L); bia = usuario(3L);
        contrato = new Contrato(); contrato.setIdContrato(10L); contrato.setStatus("APROVADO"); contrato.setResponsavel(lider);
        when(contratos.findById(10L)).thenReturn(Optional.of(contrato));
        when(contratos.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        when(contratos.findByStatus("APROVADO")).thenReturn(List.of(contrato));
        when(usuarios.findAllBySituacaoOrderByNomeCompletoAsc("ATIVO")).thenReturn(List.of(lider, ana, bia));
        for (Usuario usuario : List.of(lider, ana, bia)) when(usuarios.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(membros.save(any())).thenAnswer(i -> { ContratoEquipeMembro m = i.getArgument(0); fixos.add(m); return m; });
        doAnswer(i -> { Iterable<ContratoEquipeMembro> removidos = i.getArgument(0); removidos.forEach(fixos::remove); return null; }).when(membros).deleteAll(any());
        when(membros.existsByContrato_IdContratoAndUsuario_Id(anyLong(), anyLong())).thenAnswer(i ->
                fixos.stream().anyMatch(m -> m.getUsuario().getId().equals(i.getArgument(1))));
        when(membros.findByContrato_IdContrato(anyLong())).thenAnswer(i -> List.copyOf(fixos));
        when(tarefas.existeApoioPendente(anyLong(), anyLong())).thenAnswer(i -> criadas.stream()
                .anyMatch(t -> !t.getRaia().isConcluiTarefas() && t.getApoios().stream().anyMatch(u ->
                        u.getId().equals(i.getArgument(1)) && t.getResponsaveisEfetivos().contains(u))));
        when(tarefas.buscarApoiosPendentes(anyLong())).thenAnswer(i -> criadas.stream()
                .filter(t -> !t.getRaia().isConcluiTarefas() && !t.getApoios().isEmpty()).toList());
    }

    @Test
    void exigeSelecaoAntesDeAbrirEPessoasSelecionadasGanhamAcessoSemPermissaoGlobal() {
        assertThatThrownBy(() -> service.exigirAcesso(contrato, 1L, false)).hasMessageContaining("selecionar a equipe");
        var equipe = service.salvarEquipe(10L, 1L, List.of(2L, 3L, 2L));
        assertThat(equipe.configurada()).isTrue();
        assertThat(equipe.membros()).extracting(m -> m.id()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(service.listarDisponiveis(2L)).containsExactly(contrato);
        assertThatCode(() -> service.exigirAcesso(contrato, 3L, true)).doesNotThrowAnyException();
        verify(notificacoes).notificarParticipacaoEquipe(contrato, ana, false);
        verify(notificacoes).notificarParticipacaoEquipe(contrato, bia, false);
    }

    @Test
    void retirarMembroRemoveAcessoESalvarNovamenteNaoDuplicaNotificacoes() {
        service.salvarEquipe(10L, 1L, List.of(2L, 3L));
        clearInvocations(notificacoes);
        service.salvarEquipe(10L, 1L, List.of(2L));
        assertThat(service.podeEditar(contrato, 3L)).isFalse();
        assertThat(service.listarDisponiveis(3L)).isEmpty();
        assertThat(service.podeEditar(contrato, 2L)).isTrue();
        verifyNoInteractions(notificacoes);
    }

    @Test
    void somenteLiderConfiguraEquipeDeContratoAssinadoERecusaEquipeVaziaOuInativa() {
        assertThatThrownBy(() -> service.salvarEquipe(10L, 2L, List.of(2L))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.salvarEquipe(10L, 1L, List.of())).isInstanceOf(ResponseStatusException.class);
        ana.setSituacao("INATIVO");
        assertThatThrownBy(() -> service.salvarEquipe(10L, 1L, List.of(2L))).hasMessageContaining("usuário ativo");
        contrato.setStatus("PENDENTE");
        assertThatThrownBy(() -> service.salvarEquipe(10L, 1L, List.of(3L))).hasMessageContaining("aceito e assinado");
        verify(membros, never()).save(any());
    }

    @Test
    void apoioMantemAcessoAteConcluirTodasAsTarefasMesmoComDatasEmOutroMes() {
        service.salvarEquipe(10L, 1L, List.of(2L));
        var primeira = tarefa(20L, bia); primeira.setDataInicio(java.time.LocalDate.of(2027, 1, 1));
        service.atribuirResponsaveis(primeira, 1L, Set.of()); criadas.add(primeira);
        var segunda = tarefa(21L, bia); service.atribuirResponsaveis(segunda, 1L, Set.of()); criadas.add(segunda);
        assertThat(service.podeEditar(contrato, 3L)).isTrue();
        verify(notificacoes, times(1)).notificarParticipacaoEquipe(contrato, bia, true);
        primeira.getRaia().setConcluiTarefas(true);
        assertThat(service.podeEditar(contrato, 3L)).isTrue();
        segunda.getRaia().setConcluiTarefas(true);
        assertThat(service.podeEditar(contrato, 3L)).isFalse();
        assertThat(service.listarDisponiveis(3L)).isEmpty();
        assertThatThrownBy(() -> service.exigirAcesso(contrato, 3L, false)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.podeEditar(contrato, 2L)).isTrue();
    }

    @Test
    void apoioNaoPodeSeConcederAcessoPermanenteCriandoMaisTarefas() {
        service.salvarEquipe(10L, 1L, List.of(2L));
        var original = tarefa(20L, bia); service.atribuirResponsaveis(original, 1L, Set.of()); criadas.add(original);
        var criadaPeloApoio = tarefa(21L, bia); service.atribuirResponsaveis(criadaPeloApoio, 3L, Set.of()); criadas.add(criadaPeloApoio);
        assertThat(criadaPeloApoio.getApoios()).isEmpty();
        original.getRaia().setConcluiTarefas(true);
        assertThat(service.podeEditar(contrato, 3L)).isFalse();
    }

    @Test
    void apenasLiderPodeConvidarExternoEAutorDaTarefaNaoBypassaAcessoExpirado() {
        service.salvarEquipe(10L, 1L, List.of(2L));
        assertThatThrownBy(() -> service.atribuirResponsaveis(tarefa(20L, bia), 2L, Set.of())).hasMessageContaining("líder técnico");
        assertThatThrownBy(() -> service.exigirAcesso(contrato, 3L, true)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.listarDisponiveis(null)).isInstanceOf(ResponseStatusException.class);
    }

    private ContratoKanbanTask tarefa(Long id, Usuario usuario) {
        var t = new ContratoKanbanTask(); t.setIdTask(id); t.setContrato(contrato); t.setTitulo("Tarefa " + id);
        t.setResponsaveis(List.of(usuario)); t.setRaia(new ContratoKanbanRaia()); return t;
    }
    private Usuario usuario(Long id) { var u = new Usuario(); u.setId(id); u.setNomeCompleto("Pessoa " + id); u.setSituacao("ATIVO"); return u; }
}
