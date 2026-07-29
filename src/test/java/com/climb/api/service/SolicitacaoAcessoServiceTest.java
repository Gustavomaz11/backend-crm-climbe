package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.SolicitacaoAcesso;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import com.climb.api.model.SolicitacaoAcessoStatus;
import com.climb.api.model.Usuario;
import com.climb.api.repository.SolicitacaoAcessoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoAcessoServiceTest {

    @Mock
    private SolicitacaoAcessoRepository repository;

    @Test
    void devePersistirHistoricoAoCriarEAprovarSolicitacaoManual() {
        SolicitacaoAcessoService service = new SolicitacaoAcessoService(repository);
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
        assertEquals(1L, historico.getDecididoPor());
        assertNotNull(historico.getDecididoEm());
    }
}
