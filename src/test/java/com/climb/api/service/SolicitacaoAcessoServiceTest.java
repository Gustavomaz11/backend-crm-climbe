package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.SolicitacaoAcesso;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import com.climb.api.model.SolicitacaoAcessoStatus;
import com.climb.api.model.Usuario;
import com.climb.api.repository.SolicitacaoAcessoRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SolicitacaoAcessoServiceTest {

    @Mock
    private SolicitacaoAcessoRepository repository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private OAuth2PendingRegistrationRepository pendingRepository;
    @Mock private AcessoNotificacaoService notificacoes;
    @InjectMocks private SolicitacaoAcessoService service;

    @Test
    void devePersistirHistoricoAoCriarEAprovarSolicitacaoManual() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setNomeCompleto("Usuario Teste");
        usuario.setEmail("usuario@teste.com");
        usuario.setCpf("123");
        usuario.setContato("9999");
        Cargo cargo = new Cargo();
        cargo.setNome("Comercial");
        usuario.setCargo(cargo);

        service.registrarUsuario(usuario);

        ArgumentCaptor<SolicitacaoAcesso> captor = ArgumentCaptor.forClass(SolicitacaoAcesso.class);
        verify(repository).save(captor.capture());
        SolicitacaoAcesso historico = captor.getValue();
        verify(notificacoes).novaSolicitacao(historico);
        assertEquals(SolicitacaoAcessoStatus.PENDENTE, historico.getStatus());
        assertNotNull(historico.getCriadoEm());

        when(repository.findByOrigemAndReferenciaId(SolicitacaoAcessoOrigem.USUARIO, 7L))
                .thenReturn(Optional.of(historico));
        service.decidir(
                SolicitacaoAcessoOrigem.USUARIO,
                7L,
                SolicitacaoAcessoStatus.APROVADO,
                1L,
                "Comercial");

        assertEquals(SolicitacaoAcessoStatus.APROVADO, historico.getStatus());
        verify(notificacoes).acessoAprovado(historico);
        assertEquals(1L, historico.getDecididoPor());
        assertNotNull(historico.getDecididoEm());
    }

    @Test
    void deveRetornarNotFoundQuandoHistoricoNaoExiste() {
        when(repository.findByOrigemAndReferenciaId(SolicitacaoAcessoOrigem.USUARIO, 6L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.decidir(
                        SolicitacaoAcessoOrigem.USUARIO,
                        6L,
                        SolicitacaoAcessoStatus.RECUSADO,
                        3L,
                        null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void deveRetornarConflitoQuandoSolicitacaoJaFoiDecidida() {
        SolicitacaoAcesso historico = new SolicitacaoAcesso();
        historico.setStatus(SolicitacaoAcessoStatus.RECUSADO);
        when(repository.findByOrigemAndReferenciaId(SolicitacaoAcessoOrigem.USUARIO, 6L))
                .thenReturn(Optional.of(historico));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.decidir(
                        SolicitacaoAcessoOrigem.USUARIO,
                        6L,
                        SolicitacaoAcessoStatus.RECUSADO,
                        3L,
                        null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void deveCancelarPedidoManualPendenteAoExcluirSemDeletarUsuario() {
        var solicitacao = solicitacao(SolicitacaoAcessoOrigem.USUARIO, SolicitacaoAcessoStatus.PENDENTE);
        var usuario = new Usuario();
        usuario.setId(7L);
        usuario.setSituacao("ESPERANDO_APROVACAO");
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));

        service.excluir(SolicitacaoAcessoOrigem.USUARIO, 7L, 3L);

        assertEquals("INATIVO", usuario.getSituacao());
        assertEquals(SolicitacaoAcessoStatus.RECUSADO, solicitacao.getStatus());
        assertNotNull(solicitacao.getDecididoEm());
        assertNotNull(solicitacao.getExcluidoEm());
        assertEquals(3L, solicitacao.getExcluidoPor());
        verify(usuarioRepository).save(usuario);
        verify(repository).saveAndFlush(solicitacao);
        verifyNoInteractions(pendingRepository);
    }

    @Test
    void deveCancelarPedidoGoogleAoExcluirSemAlterarContaManualComMesmoId() {
        var solicitacao = solicitacao(SolicitacaoAcessoOrigem.GOOGLE, SolicitacaoAcessoStatus.PENDENTE);
        var pending = new OAuth2PendingRegistration();
        pending.setId(7L);
        when(pendingRepository.findById(7L)).thenReturn(Optional.of(pending));

        service.excluir(SolicitacaoAcessoOrigem.GOOGLE, 7L, 3L);

        assertEquals(true, pending.getConsumido());
        assertEquals(false, pending.getAprovado());
        assertEquals(SolicitacaoAcessoStatus.RECUSADO, solicitacao.getStatus());
        assertNotNull(solicitacao.getExcluidoEm());
        verify(pendingRepository).save(pending);
        verifyNoInteractions(usuarioRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = SolicitacaoAcessoStatus.class, names = {"APROVADO", "RECUSADO"})
    void deveExcluirSomenteHistoricoDePedidosDecididos(SolicitacaoAcessoStatus status) {
        var solicitacao = solicitacao(SolicitacaoAcessoOrigem.USUARIO, status);
        var decididoEm = LocalDateTime.now().minusDays(1);
        solicitacao.setDecididoEm(decididoEm);
        solicitacao.setDecididoPor(2L);

        service.excluir(SolicitacaoAcessoOrigem.USUARIO, 7L, 3L);

        assertEquals(status, solicitacao.getStatus());
        assertEquals(decididoEm, solicitacao.getDecididoEm());
        assertEquals(2L, solicitacao.getDecididoPor());
        assertEquals(3L, solicitacao.getExcluidoPor());
        verifyNoInteractions(usuarioRepository, pendingRepository);
    }

    @Test
    void deveRetornar404QuandoPedidoJaFoiExcluido() {
        var solicitacao = solicitacao(SolicitacaoAcessoOrigem.USUARIO, SolicitacaoAcessoStatus.PENDENTE);
        solicitacao.setExcluidoEm(LocalDateTime.now());

        var erro = assertThrows(ResponseStatusException.class,
                () -> service.excluir(SolicitacaoAcessoOrigem.USUARIO, 7L, 3L));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        verifyNoInteractions(usuarioRepository, pendingRepository);
    }

    @Test
    void deveRetornar404QuandoPedidoNaoExiste() {
        var erro = assertThrows(ResponseStatusException.class,
                () -> service.excluir(SolicitacaoAcessoOrigem.GOOGLE, 99L, 3L));
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        verifyNoInteractions(usuarioRepository, pendingRepository);
    }

    @Test
    void deveImpedirAprovacaoDePedidoExcluido() {
        var solicitacao = solicitacao(SolicitacaoAcessoOrigem.GOOGLE, SolicitacaoAcessoStatus.PENDENTE);
        solicitacao.setExcluidoEm(LocalDateTime.now());
        var erro = assertThrows(ResponseStatusException.class,
                () -> service.decidir(SolicitacaoAcessoOrigem.GOOGLE, 7L, SolicitacaoAcessoStatus.APROVADO, 3L, "Analista"));
        assertEquals(HttpStatus.CONFLICT, erro.getStatusCode());
    }

    @Test
    void devePreservarUsuarioAtivoSeHistoricoPendenteEstiverDesatualizado() {
        solicitacao(SolicitacaoAcessoOrigem.USUARIO, SolicitacaoAcessoStatus.PENDENTE);
        var usuario = new Usuario();
        usuario.setSituacao("ATIVO");
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(usuario));
        var erro = assertThrows(ResponseStatusException.class,
                () -> service.excluir(SolicitacaoAcessoOrigem.USUARIO, 7L, 3L));
        assertEquals(HttpStatus.CONFLICT, erro.getStatusCode());
        assertEquals("ATIVO", usuario.getSituacao());
    }

    @Test
    void deveListarSomentePedidosNaoExcluidos() {
        var item = new SolicitacaoAcesso();
        item.setOrigem(SolicitacaoAcessoOrigem.GOOGLE);
        item.setReferenciaId(7L);
        item.setStatus(SolicitacaoAcessoStatus.APROVADO);
        when(repository.findByExcluidoEmIsNullOrderByCriadoEmDescIdDesc()).thenReturn(List.of(item));
        var lista = service.listar();
        assertEquals(1, lista.size());
        assertEquals("GOOGLE", lista.get(0).getOrigem());
        assertEquals(7L, lista.get(0).getId());
    }

    private SolicitacaoAcesso solicitacao(SolicitacaoAcessoOrigem origem, SolicitacaoAcessoStatus status) {
        var solicitacao = new SolicitacaoAcesso();
        solicitacao.setOrigem(origem);
        solicitacao.setReferenciaId(7L);
        solicitacao.setStatus(status);
        when(repository.findByOrigemAndReferenciaId(origem, 7L)).thenReturn(Optional.of(solicitacao));
        return solicitacao;
    }
}
