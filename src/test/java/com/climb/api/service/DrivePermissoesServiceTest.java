package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.model.EmpresaDriveServico;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.EmpresaDriveServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrivePermissoesServiceTest {
    private final EmpresaDriveServicoRepository pastas = mock(EmpresaDriveServicoRepository.class);
    private final DriveAcessoService acesso = mock(DriveAcessoService.class);
    private final GoogleDriveClient drive = mock(GoogleDriveClient.class);
    private final GoogleDrivePermissoesClient permissoes = mock(GoogleDrivePermissoesClient.class);
    private final DrivePermissoesService service = new DrivePermissoesService(pastas, acesso, drive, permissoes, new GoogleDriveProperties());
    private final EmpresaDriveServico pasta = new EmpresaDriveServico();
    private final GoogleDrivePermissoesClient.Permissao conta = permissao("conta", "user", "owner", "contato@climbe.com.br");

    @BeforeEach void preparar() {
        pasta.setId(1L); pasta.setEmpresaId(100L); pasta.setServico(ServicoComercial.CFO); pasta.setPastaId("cfo");
        when(pastas.bloquear(1L)).thenReturn(Optional.of(pasta));
        when(acesso.emails(100L, ServicoComercial.CFO)).thenReturn(Set.of("ana@climbe.com.br"));
        when(permissoes.identidade()).thenReturn(new GoogleDrivePermissoesClient.Identidade("conta", conta.email()));
        when(permissoes.listar("cfo")).thenReturn(List.of(conta));
    }

    @Test void restringePastaRemoveCompartilhamentosAmplosEAcessoDiretoAntigoNosArquivos() {
        when(permissoes.listar("cfo")).thenReturn(List.of(conta,
                permissao("ex", "user", "writer", "ex@climbe.com.br"), permissao("publico", "anyone", "reader", null),
                permissao("dominio", "domain", "reader", null), permissao("grupo", "group", "writer", "grupo@climbe.com.br"),
                new GoogleDrivePermissoesClient.Permissao("herdado", "user", "reader", "outro@climbe.com.br", true, true)));
        when(drive.listar("cfo")).thenReturn(List.of(new GoogleDriveClient.Arquivo("pdf", "Balanço.pdf", "application/pdf", 1, List.of("cfo"), false, null)));
        when(permissoes.listar("pdf")).thenReturn(List.of(conta, permissao("ex", "user", "writer", "ex@climbe.com.br")));
        service.sincronizar(1L);
        var ordem = inOrder(permissoes);
        ordem.verify(permissoes).limitar("cfo", true);
        ordem.verify(permissoes).remover("cfo", "ex");
        verify(permissoes).remover("cfo", "publico"); verify(permissoes).remover("cfo", "dominio"); verify(permissoes).remover("cfo", "grupo");
        verify(permissoes).remover("pdf", "ex"); verify(permissoes).conceder("cfo", "ana@climbe.com.br", "writer");
        verify(permissoes, never()).remover("cfo", "conta"); verify(permissoes, never()).remover("cfo", "herdado");
        assertThat(pasta.getPermissoesErro()).isNull(); assertThat(pasta.getPermissoesSincronizadasEm()).isNotNull();
    }

    @Test void acessoJaCorretoNaoDuplicaConvitesENaoRegravaPermissoes() {
        when(permissoes.listar("cfo")).thenReturn(List.of(conta, permissao("ana", "user", "writer", "ana@climbe.com.br")));
        service.sincronizar(1L);
        verify(permissoes, never()).conceder(any(), any(), any()); verify(permissoes, never()).alterar(any(), any(), any());
        verify(permissoes, never()).remover(any(), any());
    }

    @Test void servicoEncerradoRevogaEquipePreservandoContaTecnica() {
        when(acesso.emails(100L, ServicoComercial.CFO)).thenReturn(Set.of());
        when(permissoes.listar("cfo")).thenReturn(List.of(conta, permissao("ana", "user", "writer", "ana@climbe.com.br")));
        service.sincronizar(1L);
        verify(permissoes).remover("cfo", "ana"); verify(permissoes, never()).remover("cfo", "conta");
        verify(permissoes, never()).conceder(any(), any(), any());
    }

    @Test void falhaAoLimitarNaoConcedeAcessoEAgendaNovaTentativa() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Conta sem permissão para limitar"))
                .when(permissoes).limitar("cfo", true);
        service.sincronizar(1L);
        assertThat(pasta.getPermissoesErro()).contains("sem permissão");
        assertThat(pasta.getPermissoesTentarEm()).isAfter(LocalDateTime.now());
        assertThat(pasta.getPermissoesSincronizadasEm()).isNull();
        verify(permissoes, never()).conceder(any(), any(), any());
    }

    @Test void protegeContaTecnicaAntesDeDesabilitarHeranca() {
        when(permissoes.listar("cfo")).thenReturn(List.of(new GoogleDrivePermissoesClient.Permissao("conta", "user", "writer", conta.email(), true, false)));
        service.sincronizar(1L);
        var ordem = inOrder(permissoes);
        ordem.verify(permissoes).conceder("cfo", conta.email(), "writer");
        ordem.verify(permissoes).limitar("cfo", true);
    }
    private static GoogleDrivePermissoesClient.Permissao permissao(String id, String tipo, String papel, String email) {
        return new GoogleDrivePermissoesClient.Permissao(id, tipo, papel, email, false, false);
    }
}
