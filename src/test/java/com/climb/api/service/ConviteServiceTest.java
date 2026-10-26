package com.climb.api.service;

import com.climb.api.model.dto.ConviteLoteRequestDTO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConviteServiceTest {
    @Test void normalizaEDeduplicaSemCadastrarOuAprovarDestinatarios() {
        var fila = mock(EmailNotificacaoFilaService.class);
        var service = new ConviteService(fila);
        var resultado = service.convidar(new ConviteLoteRequestDTO(List.of(
                " Socio@climbe.com.br ", "socio@climbe.com.br", "outra@example.com")));
        assertThat(resultado.agendados()).isEqualTo(2);
        assertThat(resultado.duplicadosIgnorados()).isEqualTo(1);
        verify(fila).enfileirar(new NotificacaoEntregaService.EmailNotificacao(
                "socio@climbe.com.br", ConviteService.ASSUNTO, ConviteService.MENSAGEM, "/convite"));
        verify(fila).enfileirar(new NotificacaoEntregaService.EmailNotificacao(
                "outra@example.com", ConviteService.ASSUNTO, ConviteService.MENSAGEM, "/convite"));
        verifyNoMoreInteractions(fila);
    }
}
