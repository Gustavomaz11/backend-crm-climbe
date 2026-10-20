package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KanbanPrazoAvisoServiceTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final NotificacaoRepository avisos = mock(NotificacaoRepository.class);
    private final KanbanPrazoNotificacaoRepository registros = mock(KanbanPrazoNotificacaoRepository.class);
    private final KanbanPrazoAvisoService service = new KanbanPrazoAvisoService(usuarios, avisos, registros);
    private final Set<String> enviadas = new HashSet<>();
    private final LocalDate prazo = LocalDate.of(2026, 11, 10);
    private Usuario usuario;

    @BeforeEach void setup() {
        usuario = new Usuario(); usuario.setId(1L); usuario.setSituacao("ATIVO");
        when(usuarios.findByIdForUpdate(1L)).thenReturn(Optional.of(usuario));
        when(registros.buscarChaves(anyCollection())).thenAnswer(i -> {
            Collection<String> chaves = i.getArgument(0);
            var existentes = new HashSet<>(enviadas); existentes.retainAll(chaves); return existentes;
        });
        when(registros.saveAll(any())).thenAnswer(i -> {
            List<KanbanPrazoNotificacao> rows = i.getArgument(0); rows.forEach(r -> enviadas.add(r.getChave())); return rows;
        });
    }

    @Test void cadaMarcoGeraAvisoUmaVezEAtrasoNaoRepeteNosDiasSeguintes() {
        for (int marco : List.of(3, 1, 0, -1)) {
            var atividade = atividade(TarefaTipo.CONTRATO, marco, prazo);
            service.notificar(1L, List.of(atividade, atividade), prazo.minusDays(marco));
            service.notificar(1L, List.of(atividade), prazo.minusDays(marco));
        }
        service.notificar(1L, List.of(atividade(TarefaTipo.CONTRATO, -1, prazo)), prazo.plusDays(7));
        var salvo = ArgumentCaptor.forClass(Notificacao.class); verify(avisos, times(4)).save(salvo.capture());
        assertThat(salvo.getAllValues()).extracting(Notificacao::getTipo)
                .containsExactly("TAREFA_VENCIMENTO", "TAREFA_VENCIMENTO", "TAREFA_VENCIMENTO", "TAREFA_ATRASADA");
        assertThat(salvo.getAllValues()).allSatisfy(aviso -> {
            assertThat(aviso.getUsuario()).isSameAs(usuario);
            assertThat(aviso.getLida()).isFalse();
            assertThat(aviso.getDestino()).isEqualTo("/contratos/kanban?contrato=10&tarefaId=20&servico=CFO");
            assertThat(aviso.getMensagem()).contains("Análise CFO", "Empresa", "CFO", "10/11/2026");
        });
        assertThat(salvo.getAllValues().get(3).getMensagem()).contains("atrasada há 1 dia");
        assertThat(enviadas).hasSize(4).allMatch(chave -> chave.startsWith("APP:"));
    }

    @Test void envioDeEmailAnteriorNaoImpedeAvisoNoSistemaENovoPrazoGeraNovoAviso() {
        var atividade = atividade(TarefaTipo.CONTRATO, 0, prazo);
        enviadas.add(atividade.chave(1L));
        service.notificar(1L, List.of(atividade), prazo);
        service.notificar(1L, List.of(atividade(TarefaTipo.CONTRATO, 0, prazo.plusDays(1))), prazo.plusDays(1));
        verify(avisos, times(2)).save(any(Notificacao.class));
        assertThat(enviadas).hasSize(3);
    }

    @Test void usuarioSemEmailRecebeAvisoEIdsIguaisDeTiposDistintosNaoColidem() {
        service.notificar(1L, List.of(atividade(TarefaTipo.CONTRATO, 0, prazo), atividade(TarefaTipo.COMERCIAL, 0, prazo)), prazo);
        verify(avisos, times(2)).save(any(Notificacao.class));
        assertThat(enviadas).hasSize(2);
    }

    @Test void usuarioInativoNaoRecebeAvisos() {
        usuario.setSituacao("INATIVO");
        service.notificar(1L, List.of(atividade(TarefaTipo.CONTRATO, 0, prazo)), prazo);
        verifyNoInteractions(avisos, registros);
    }

    private KanbanPrazoAtividade atividade(TarefaTipo tipo, int marco, LocalDate data) {
        return new KanbanPrazoAtividade(tipo, 20L, "Análise CFO", "Empresa", "CFO", "Ana", data, marco,
                "https://app.test/contratos/kanban?contrato=10&tarefaId=20&servico=CFO");
    }
}
