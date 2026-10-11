package com.climb.api.security;

import com.climb.api.controller.*;
import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.repository.*;
import com.climb.api.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApiAuthorizationSecurityTest {
    @Configuration @EnableMethodSecurity static class Security {}
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"usuarioEditar", "usuarioExcluir", "associarPermissao", "removerPermissao", "criarPermissao", "editarPermissao", "excluirPermissao", "enviarDocumento", "uploadArquivo", "listarTodasNotificacoes"})
    void usuarioComumNaoExecutaAcoesAdministrativasOuRestritas(String action) {
        var rbac = mock(RbacService.class);
        var usuarioService = mock(UsuarioService.class);
        var associacoes = mock(UsuarioPermissaoService.class);
        var permissoes = mock(PermissaoService.class);
        var documentos = mock(DocumentoService.class);
        var arquivos = mock(CloudflareR2ArquivoStorageService.class);
        var notificacoes = mock(NotificacaoService.class);
        var auth = new UsernamePasswordAuthenticationToken("comum@test.local", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(7L); SecurityContextHolder.getContext().setAuthentication(auth);
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(Security.class);
            ctx.registerBean("permissaoAcesso", PermissaoAcesso.class, () -> new PermissaoAcesso(new AuthenticatedUserProvider(), rbac,
                    mock(ContratoRepository.class), mock(ContratoEquipeService.class), mock(RevisaoDocumentoRepository.class)));
            ctx.registerBean(UsuarioController.class, () -> new UsuarioController(usuarioService, rbac, mock(OAuth2PendingService.class),
                    mock(AuthenticationService.class), mock(SolicitacaoAcessoService.class), mock(PerfilService.class)));
            ctx.registerBean(UsuarioPermissaoController.class, () -> new UsuarioPermissaoController(associacoes));
            ctx.registerBean(PermissaoController.class, () -> new PermissaoController(permissoes));
            ctx.registerBean(DocumentoUploadController.class, () -> new DocumentoUploadController(documentos));
            ctx.registerBean(ArquivoController.class, () -> new ArquivoController(arquivos));
            ctx.registerBean(NotificacaoController.class, () -> new NotificacaoController(notificacoes));
            ctx.refresh();
            assertThatThrownBy(() -> {
                switch (action) {
                    case "usuarioEditar" -> ctx.getBean(UsuarioController.class).atualizar(8L, new UsuarioRequestDTO());
                    case "usuarioExcluir" -> ctx.getBean(UsuarioController.class).deletar(8L);
                    case "associarPermissao" -> ctx.getBean(UsuarioPermissaoController.class).criar(new UsuarioPermissaoDTO());
                    case "removerPermissao" -> ctx.getBean(UsuarioPermissaoController.class).deletar(1L);
                    case "criarPermissao" -> ctx.getBean(PermissaoController.class).criar(new Permissao());
                    case "editarPermissao" -> ctx.getBean(PermissaoController.class).atualizar(1L, new Permissao());
                    case "excluirPermissao" -> ctx.getBean(PermissaoController.class).deletar(1L);
                    case "enviarDocumento" -> ctx.getBean(DocumentoUploadController.class).enviar(1L, mock(MultipartFile.class));
                    case "uploadArquivo" -> ctx.getBean(ArquivoController.class).salvar(mock(MultipartFile.class), "arquivos");
                    case "listarTodasNotificacoes" -> ctx.getBean(NotificacaoController.class).listar();
                    default -> throw new IllegalArgumentException(action);
                }
            }).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(usuarioService, associacoes, permissoes, documentos, arquivos, notificacoes);
        }
    }
}
