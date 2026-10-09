package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KanbanPrazoEnvioServiceTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final KanbanPrazoNotificacaoRepository notificacoes = mock(KanbanPrazoNotificacaoRepository.class);
    private final KanbanPrazoEmailService email = mock(KanbanPrazoEmailService.class);
    private final KanbanPrazoEnvioService service = new KanbanPrazoEnvioService(usuarios, notificacoes, email);
    private final Set<String> enviadas = new HashSet<>();
    private final LocalDate hoje = LocalDate.of(2026, 11, 10);
    private Usuario usuario;
    @BeforeEach void setup() {
        usuario = new Usuario(); usuario.setId(1L); usuario.setSituacao("ATIVO"); usuario.setEmail("test@example.test");
        when(usuarios.findByIdForUpdate(1L)).thenReturn(Optional.of(usuario));
        when(notificacoes.buscarChaves(anyCollection())).thenAnswer(i -> {
            Collection<String> chaves = i.getArgument(0); var existentes = new HashSet<>(enviadas); existentes.retainAll(chaves); return existentes;
        });
        when(notificacoes.saveAll(any())).thenAnswer(i -> {
            List<KanbanPrazoNotificacao> rows = i.getArgument(0); rows.forEach(r -> enviadas.add(r.getChave())); return rows;
        });
    }
    @Test void agrupaERegistraSomenteUmaVezCadaPrazoMarcoDestinatarioETipo() {
        var lista = List.of(atividade(TarefaTipo.COMERCIAL, 0, hoje), atividade(TarefaTipo.CONTRATO, 0, hoje));
        when(email.enviar(any(), anyList(), any())).thenReturn(true);
        service.enviar(1L, lista, hoje); service.enviar(1L, lista, hoje);
        assertThat(enviadas).hasSize(2); verify(email).enviar(usuario, lista, hoje);
        service.enviar(1L, List.of(atividade(TarefaTipo.COMERCIAL, -1, hoje)), hoje.plusDays(1));
        service.enviar(1L, List.of(atividade(TarefaTipo.COMERCIAL, 3, hoje.plusDays(4))), hoje.plusDays(1));
        assertThat(enviadas).hasSize(4);
        var ordem = inOrder(usuarios, notificacoes, email);
        ordem.verify(usuarios).findByIdForUpdate(1L); ordem.verify(notificacoes).buscarChaves(anyCollection());
        ordem.verify(email).enviar(usuario, lista, hoje);
    }
    @Test void falhaDeSmtpNaoMarcaEnviadoEPodeTentarNovamente() {
        var lista = List.of(atividade(TarefaTipo.COMERCIAL, 3, hoje.plusDays(3)));
        when(email.enviar(any(), anyList(), any())).thenReturn(false, true);
        service.enviar(1L, lista, hoje); assertThat(enviadas).isEmpty();
        service.enviar(1L, lista, hoje); assertThat(enviadas).hasSize(1);
    }
    @Test void naoEnviaParaUsuarioInativo() {
        usuario.setSituacao("INATIVO");
        service.enviar(1L, List.of(atividade(TarefaTipo.COMERCIAL, 0, hoje)), hoje);
        verifyNoInteractions(email); assertThat(enviadas).isEmpty();
    }
    private KanbanPrazoAtividade atividade(TarefaTipo tipo, int marco, LocalDate prazo) {
        return new KanbanPrazoAtividade(tipo, 10L, "Título", "Empresa", "Serviço", "Pessoa", prazo, marco, "https://app.test");
    }
}
