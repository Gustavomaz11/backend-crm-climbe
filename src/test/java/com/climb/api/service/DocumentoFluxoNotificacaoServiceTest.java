package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.repository.NotificacaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.climb.api.model.enums.RevisaoDocumentoStatus;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentoFluxoNotificacaoServiceTest {
    @ParameterizedTest
    @EnumSource(value = RevisaoDocumentoStatus.class, names = {"AJUSTES_SOLICITADOS", "REPROVADO"})
    void respostasDaPropostaAvisamOComercialDaNegociacaoEIncluemComentario(RevisaoDocumentoStatus status) {
        var notificacoes = mock(NotificacaoRepository.class); var usuarios = mock(UsuarioRepository.class);
        var service = new DocumentoFluxoNotificacaoService(notificacoes, usuarios);
        var comercial = usuario(1L); var preparador = usuario(2L);
        var negocio = new PipelineVendasNegocio(); negocio.setResponsavel(comercial);
        var proposta = new Proposta(); proposta.setIdProposta(10L); proposta.setUsuario(preparador); proposta.setNegocio(negocio);
        service.propostaRespondidaPeloCliente(proposta, status, 2, "Ajustar valores.");
        var salvo = ArgumentCaptor.forClass(Notificacao.class); verify(notificacoes).save(salvo.capture());
        assertThat(salvo.getValue().getUsuario()).isSameAs(comercial);
        assertThat(salvo.getValue().getMensagem()).contains("versão 2", "Ajustar valores.");
        verifyNoInteractions(usuarios);
    }

    @ParameterizedTest
    @EnumSource(value = RevisaoDocumentoStatus.class, names = {"AGUARDANDO_CLIENTE", "APROVADO"})
    void outrosEstadosNaoGeramAvisosDeRevisaoOuReprovacao(RevisaoDocumentoStatus status) {
        var notificacoes = mock(NotificacaoRepository.class); var usuarios = mock(UsuarioRepository.class);
        var service = new DocumentoFluxoNotificacaoService(notificacoes, usuarios);
        service.propostaRespondidaPeloCliente(new Proposta(), status, 1, null);
        service.contratoRespondidoPeloCliente(new Contrato(), status, 1, null);
        verifyNoInteractions(notificacoes, usuarios);
    }

    @Test void aceiteAvisaResponsavelDaNegociacaoMesmoQuandoOutraPessoaCriouProposta() {
        var notificacoes = mock(NotificacaoRepository.class);
        var usuarios = mock(UsuarioRepository.class);
        var service = new DocumentoFluxoNotificacaoService(notificacoes, usuarios);
        Usuario comercial = usuario(1L), preparador = usuario(2L);
        var negocio = new PipelineVendasNegocio(); negocio.setResponsavel(comercial);
        var proposta = new Proposta(); proposta.setIdProposta(10L); proposta.setUsuario(preparador); proposta.setNegocio(negocio);
        service.propostaAceita(proposta, 1);
        var salvo = ArgumentCaptor.forClass(Notificacao.class); verify(notificacoes).save(salvo.capture());
        assertThat(salvo.getValue().getUsuario()).isSameAs(comercial);
        assertThat(salvo.getValue().getTipo()).isEqualTo("PROPOSTA_ACEITA");
        verifyNoInteractions(usuarios);
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario(); usuario.setId(id); usuario.setSituacao("ATIVO"); return usuario;
    }
}
