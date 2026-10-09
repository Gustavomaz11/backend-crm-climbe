package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.model.enums.TarefaTipo;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class KanbanPrazoEmailServiceTest {
    @Test void incluiDetalhesPrazosResponsaveisLinkEEscapaConteudo() {
        var email = mock(EmailService.class); var service = new KanbanPrazoEmailService(email);
        var usuario = new Usuario(); usuario.setNomeCompleto("Diretor"); usuario.setEmail("test@example.test");
        LocalDate hoje = LocalDate.of(2026, 11, 10);
        var atividades = List.of(
                new KanbanPrazoAtividade(TarefaTipo.COMERCIAL, 1L, "<script>alert(1)</script>", "Empresa & Cia", "VALUATION", "Ana, Bia", hoje.plusDays(3), 3, "https://app.test?a=1&b=2"),
                new KanbanPrazoAtividade(TarefaTipo.CONTRATO, 2L, "Relatório", "Empresa", "CONTABILIDADE", "Caio", hoje.minusDays(1), -1, "https://app.test"));
        when(email.enviarEmailComConteudoHtml(any(), any(), any(), any(), any())).thenReturn(true);
        assertThat(service.enviar(usuario, atividades, hoje)).isTrue();
        verify(email).enviarEmailComConteudoHtml(eq("test@example.test"), contains("atrasadas"), any(),
                argThat(html -> html.contains("Ana, Bia") && html.contains("13/11/2026") && html.contains("Vence em 3 dias")
                        && html.contains("Atrasada h&aacute; 1 dia") && html.contains("&lt;script&gt;") && !html.contains("<script>")
                        && html.contains("Empresa &amp; Cia") && html.contains("a=1&amp;b=2")), any());
    }
}
