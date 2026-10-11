package com.climb.api.security;

import com.climb.api.model.Contrato;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.RevisaoDocumentoRepository;
import com.climb.api.service.ContratoEquipeService;
import com.climb.api.service.RbacService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PermissaoAcessoTest {
    private final AuthenticatedUserProvider usuario = mock(AuthenticatedUserProvider.class);
    private final RbacService rbac = mock(RbacService.class);
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ContratoEquipeService equipe = mock(ContratoEquipeService.class);
    private final PermissaoAcesso acesso = new PermissaoAcesso(usuario, rbac, contratos, equipe, mock(RevisaoDocumentoRepository.class));

    @Test void juridicoLeContratoEPropostaVinculadaSemReceberOutrasAcoes() {
        when(usuario.getUserId()).thenReturn(7L);
        when(rbac.temPermissao(7L, PermissaoCodigo.JURIDICO_CONTRATO_LER)).thenReturn(true);
        when(contratos.findByProposta_IdProposta(3L)).thenReturn(Optional.of(new Contrato()));
        assertThat(acesso.lerContrato(12L)).isTrue();
        assertThat(acesso.lerProposta(3L)).isTrue();
        assertThat(acesso.lerProposta(4L)).isFalse();
        assertThat(acesso.contrato("ENVIAR_CLIENTE")).isFalse();
        assertThat(acesso.contrato("DELETAR")).isFalse();
        assertThat(acesso.tem("PROPOSTA_LER")).isFalse();
    }

    @Test void lerNaoAutorizaEscrita() {
        when(usuario.getUserId()).thenReturn(7L);
        when(rbac.temPermissao(7L, PermissaoCodigo.CONTRATO_LER)).thenReturn(true);
        assertThat(acesso.tem("CONTRATO_LER")).isTrue();
        assertThat(acesso.tem("CONTRATO_CRIAR")).isFalse();
        assertThat(acesso.tem("CONTRATO_EDITAR")).isFalse();
        assertThat(acesso.tem("CONTRATO_DELETAR")).isFalse();
    }

    @Test void equipeLeContratoEPropostaVinculadaSemGanharPermissaoDeEdicao() {
        when(usuario.getUserId()).thenReturn(7L);
        var contrato = new Contrato(); contrato.setIdContrato(12L);
        when(contratos.findById(12L)).thenReturn(Optional.of(contrato));
        when(contratos.findByProposta_IdProposta(3L)).thenReturn(Optional.of(contrato));
        when(equipe.podeVisualizar(contrato, 7L)).thenReturn(true);
        assertThat(acesso.lerContrato(12L)).isTrue();
        assertThat(acesso.lerProposta(3L)).isTrue();
        assertThat(acesso.lerContrato(13L)).isFalse();
        assertThat(acesso.lerProposta(4L)).isFalse();
        assertThat(acesso.tem("CONTRATO_EDITAR")).isFalse();
        assertThat(acesso.tem("PROPOSTA_EDITAR")).isFalse();
    }
}
