package com.climb.api.controller;

import com.climb.api.model.*;
import com.climb.api.model.enums.RespostaConviteReuniao;
import com.climb.api.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipanteReuniaoControllerTest {
    @Mock ParticipanteReuniaoRepository participantes;
    @Mock ReuniaoRepository reunioes;
    @InjectMocks ParticipanteReuniaoController controller;

    @BeforeEach void autenticar() {
        var auth = new UsernamePasswordAuthenticationToken("org@climbe.com.br", null, List.of()); auth.setDetails(1L);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(participantes.existsByReuniao_IdReuniaoAndUsuario_Id(3L, 1L)).thenReturn(true);
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L); reuniao.setOrganizadorId(1L);
        when(reunioes.findById(3L)).thenReturn(Optional.of(reuniao));
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void criacaoConfirmaOrganizadorUsandoDadosDaReuniaoSalva() {
        var participante = participante(1L);
        controller.criar(participante);
        assertThat(participante.getResposta()).isEqualTo(RespostaConviteReuniao.ACEITA);
        assertThat(participante.getRespondidoEm()).isNotNull();
        assertThat(participante.isAceiteSincronizadoGoogle()).isTrue();
        verify(participantes).save(participante);
    }

    @Test void mudarUsuarioParaOrganizadorConfirmaEmVezDeDeixarPendente() {
        var atual = participante(2L);
        when(participantes.findById(8L)).thenReturn(Optional.of(atual));
        controller.atualizar(8L, participante(1L));
        assertThat(atual.getResposta()).isEqualTo(RespostaConviteReuniao.ACEITA);
        assertThat(atual.getRespondidoEm()).isNotNull();
    }

    @Test void convidadoNaoRecebeAceiteAutomatico() {
        var participante = participante(2L);
        controller.criar(participante);
        assertThat(participante.getResposta()).isEqualTo(RespostaConviteReuniao.PENDENTE);
        assertThat(participante.getRespondidoEm()).isNull();
    }

    private ParticipanteReuniao participante(Long usuarioId) {
        var usuario = new Usuario(); usuario.setId(usuarioId);
        var reuniao = new Reuniao(); reuniao.setIdReuniao(3L);
        var p = new ParticipanteReuniao(); p.setUsuario(usuario); p.setReuniao(reuniao); return p;
    }
}
