package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.NotificacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContratoEnvioNotificacaoTest {
    @Test
    void deveAvisarResponsavelComercialMesmoQuandoOutroUsuarioEnviou() {
        var repository = mock(NotificacaoRepository.class);
        var email = mock(EmailService.class);
        var service = new ContratoNotificacaoService(repository, email, 30);
        Usuario comercial = new Usuario(); comercial.setId(1L); comercial.setEmail("comercial@example.test");
        comercial.setNomeCompleto("Comercial");
        Usuario preparador = new Usuario(); preparador.setId(3L);
        Usuario tecnico = new Usuario(); tecnico.setId(2L);
        Contrato contrato = new Contrato(); contrato.setIdContrato(10L); contrato.setEmpresaNomeFantasia("Empresa");
        contrato.setResponsavelComercial(comercial); contrato.setUsuario(preparador); contrato.setResponsavel(tecnico);
        service.notificarContratoEnviado(contrato);
        var captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isSameAs(comercial);
        assertThat(captor.getValue().getTipo()).isEqualTo("CONTRATO_ENVIADO");
        verify(email).enviarEmail(eq("comercial@example.test"), eq("Contrato enviado ao cliente"), anyString());
        verifyNoMoreInteractions(email);
    }
}
