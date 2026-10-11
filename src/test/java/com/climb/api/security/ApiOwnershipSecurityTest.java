package com.climb.api.security;

import com.climb.api.controller.*;
import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.repository.*;
import com.climb.api.service.*;
import org.junit.jupiter.api.*;
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
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiOwnershipSecurityTest {
    @Configuration @EnableMethodSecurity static class Security {}
    AnnotationConfigApplicationContext context;
    RbacService rbac;
    NotificacaoRepository repository;
    NotificacaoService notifications;
    UsuarioPermissaoService associations;
    UsuarioService users;
    CloudflareR2ArquivoStorageService files;
    DocumentoService documents;

    @BeforeEach void setup() {
        rbac=mock(RbacService.class);repository=mock(NotificacaoRepository.class);notifications=mock(NotificacaoService.class);
        associations=mock(UsuarioPermissaoService.class);users=mock(UsuarioService.class);
        files=mock(CloudflareR2ArquivoStorageService.class);documents=mock(DocumentoService.class);
        var auth=new UsernamePasswordAuthenticationToken("user@test.local",null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(7L);SecurityContextHolder.getContext().setAuthentication(auth);
        context=new AnnotationConfigApplicationContext();context.register(Security.class);
        var permissions=new PermissaoAcesso(new AuthenticatedUserProvider(),rbac,mock(ContratoRepository.class),mock(ContratoEquipeService.class),mock(RevisaoDocumentoRepository.class));
        context.registerBean("permissaoAcesso",PermissaoAcesso.class,()->permissions);
        context.registerBean("notificacaoAcesso",NotificacaoAcesso.class,()->new NotificacaoAcesso(repository,permissions));
        context.registerBean(NotificacaoController.class,()->new NotificacaoController(notifications));
        context.registerBean(UsuarioPermissaoController.class,()->new UsuarioPermissaoController(associations));
        context.registerBean(UsuarioController.class,()->new UsuarioController(users,rbac,mock(OAuth2PendingService.class),mock(AuthenticationService.class),mock(SolicitacaoAcessoService.class),mock(PerfilService.class)));
        context.registerBean(ArquivoController.class,()->new ArquivoController(files));
        context.registerBean(DocumentoUploadController.class,()->new DocumentoUploadController(documents));
        context.refresh();
    }
    @AfterEach void close() { context.close();SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"buscar", "ler", "excluir"})
    void somenteDonoOuAdministradorPodeAcessarNotificacao(String action) {
        var own=new Notificacao(); var owner=new Usuario();owner.setId(7L);own.setUsuario(owner);
        when(repository.findById(1L)).thenReturn(Optional.of(own));
        var another=new Notificacao();var other=new Usuario();other.setId(8L);another.setUsuario(other);
        when(repository.findById(2L)).thenReturn(Optional.of(another));
        invokeNotification(action,1L);
        clearInvocations(notifications);
        assertThatThrownBy(()->invokeNotification(action,2L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(notifications);
        when(rbac.temPermissao(7L,PermissaoCodigo.PERMITIR_ACESSO)).thenReturn(true);
        invokeNotification(action,2L);
        assertThat(mockingDetails(notifications).getInvocations()).hasSize(1);
    }

    @Test void usuarioPodeConsultarSuasPermissoesMasNaoAsDeOutraPessoaNemSeAutopromover() {
        var controller=context.getBean(UsuarioPermissaoController.class);
        controller.listarPorUsuario(7L);
        verify(associations).listarPorUsuario(7L);
        assertThatThrownBy(()->controller.listarPorUsuario(8L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.criar(new UsuarioPermissaoDTO())).isInstanceOf(AccessDeniedException.class);
        verify(associations,never()).criar(any(),any());
    }

    @Test void administradorMantemGerenciamentoDeUsuariosEPermissoes() {
        when(rbac.temPermissao(7L,PermissaoCodigo.PERMITIR_ACESSO)).thenReturn(true);
        var request=new UsuarioRequestDTO();context.getBean(UsuarioController.class).atualizar(8L,request);
        verify(users).atualizar(8L,request);
        context.getBean(UsuarioController.class).deletar(8L);verify(users).deletar(8L,7L);
        var grant=new UsuarioPermissaoDTO();context.getBean(UsuarioPermissaoController.class).criar(grant);
        verify(associations).criar(grant.getUsuarioId(),grant.getPermissaoId());
    }

    @Test void uploadsExigemAPermissaoEspecifica() {
        var file=mock(MultipartFile.class);
        when(rbac.temPermissao(7L,PermissaoCodigo.ARQUIVO_UPLOAD)).thenReturn(true);
        context.getBean(ArquivoController.class).salvar(file,"arquivos");
        assertThatThrownBy(()->context.getBean(DocumentoUploadController.class).enviar(1L,file)).isInstanceOf(AccessDeniedException.class);
        when(rbac.temPermissao(7L,PermissaoCodigo.DOCUMENTO_JURIDICO_EDITAR)).thenReturn(true);
        context.getBean(DocumentoUploadController.class).enviar(1L,file);
        assertThat(mockingDetails(files).getInvocations()).isNotEmpty();
        assertThat(mockingDetails(documents).getInvocations()).hasSize(1);
    }

    private void invokeNotification(String action,Long id) {
        var controller=context.getBean(NotificacaoController.class);
        switch(action) { case "buscar"->controller.buscarPorId(id);case "ler"->controller.marcarComoLida(id);case "excluir"->controller.deletar(id);default->throw new IllegalArgumentException(); }
    }
}
