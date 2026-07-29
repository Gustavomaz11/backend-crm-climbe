package com.climb.api.service;

import com.climb.api.model.Cargo;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.Permissao;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2PendingServiceTest {

    @Mock
    private OAuth2PendingRegistrationRepository repository;

    @Mock
    private AprovacaoAcessoService aprovacaoAcessoService;

    @InjectMocks
    private OAuth2PendingService service;

    @Test
    void deveExigirCargoEPermissoesNaAprovacao() {
        OAuth2PendingRegistration pending = pendingValido();
        when(repository.findById(42L)).thenReturn(java.util.Optional.of(pending));
        when(aprovacaoAcessoService.resolver(null, Set.of()))
                .thenThrow(new IllegalArgumentException("Cargo e obrigatorio"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.aprovar(42L, 1L, null, Set.of()));

        assertEquals("Cargo e obrigatorio", error.getMessage());
    }

    @Test
    void devePersistirCargoEPermissoesEscolhidosPeloAprovador() {
        OAuth2PendingRegistration pending = pendingValido();
        Cargo cargo = cargo(2L, "CEO");
        Permissao permissao = permissao(10L, "PERMITIR_ACESSO");
        AprovacaoAcessoService.AtribuicaoAcesso atribuicao =
                new AprovacaoAcessoService.AtribuicaoAcesso(cargo, Set.of(permissao));

        when(repository.findById(42L)).thenReturn(java.util.Optional.of(pending));
        when(aprovacaoAcessoService.resolver(2L, Set.of(10L))).thenReturn(atribuicao);

        service.aprovar(42L, 1L, 2L, Set.of(10L));

        assertTrue(pending.getAprovado());
        assertEquals(cargo, pending.getCargo());
        assertEquals(Set.of(permissao), pending.getPermissoes());
        verify(repository).save(pending);
    }

    private OAuth2PendingRegistration pendingValido() {
        OAuth2PendingRegistration pending = new OAuth2PendingRegistration();
        pending.setId(42L);
        pending.setConsumido(false);
        pending.setAprovado(false);
        pending.setExpiraEm(LocalDateTime.now().plusDays(1));
        return pending;
    }

    private Cargo cargo(Long id, String nome) {
        Cargo cargo = new Cargo();
        cargo.setId(id);
        cargo.setNome(nome);
        return cargo;
    }

    private Permissao permissao(Long id, String codigo) {
        Permissao permissao = new Permissao();
        permissao.setIdPermissao(id);
        permissao.setCodigo(codigo);
        permissao.setDescricao(codigo);
        return permissao;
    }
}
