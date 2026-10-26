package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.UsuarioRequestDTO;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Set;
import java.util.LinkedHashSet;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false",
        "logging.level.root=WARN", "app.notificacoes.email.enabled=false",
        "app.pipeline.cadencia.atraso-inicial-ms=86400000"})
@ActiveProfiles("test")
@Transactional
class AcessoNotificacaoIntegrationTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired PermissaoRepository permissoes;
    @Autowired GrupoPermissaoRepository grupos;
    @Autowired CargoRepository cargos;
    @Autowired NotificacaoRepository avisos;
    @Autowired EmailNotificacaoPendenteRepository emails;
    @Autowired SolicitacaoAcessoRepository solicitacoes;
    @Autowired UsuarioService usuarioService;
    @Autowired OAuth2PendingService google;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean EmailService email;
    private Permissao acesso;
    private Permissao leitura;
    private Usuario administrador;
    private Usuario administradorComExtras;
    private Cargo cargo;

    @BeforeEach void preparar() {
        // O teste de rollback prepara os próprios registros dentro da transação que será desfeita.
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) return;
        prepararCatalogo();
    }

    private void prepararCatalogo() {
        acesso = permissao("PERMITIR_ACESSO"); leitura = permissao("CONTRATO_LER");
        var grupo = new GrupoPermissao(); grupo.setNome(" Administrador "); grupo.setPermissoes(new LinkedHashSet<>(Set.of(acesso, leitura))); grupos.save(grupo);
        cargo = new Cargo(); cargo.setNome("Analista"); cargo = cargos.save(cargo);
        administrador = usuario("admin", "ATIVO", Set.of(acesso, leitura));
        administradorComExtras = usuario("admin-extra", "ATIVO", Set.of(acesso, leitura, permissao("PROPOSTA_LER")));
        usuario("permissao-parcial", "ATIVO", Set.of(acesso));
        usuario("inativo", "INATIVO", Set.of(acesso, leitura));
        usuario("revogado", "REVOGADO", Set.of(acesso, leitura));
        usuario("em-aprovacao", "ESPERANDO_APROVACAO", Set.of(acesso, leitura));
        usuario("em-cadastro", "COMPLETAR_CADASTRO", Set.of(acesso, leitura));
        usuarios.flush();
    }

    @Test void identificaTodosOsAdministradoresAtivosInclusiveComPermissoesExtras() {
        assertThat(usuarios.buscarAdministradoresAtivos()).extracting(Usuario::getId)
                .containsExactly(administrador.getId(), administradorComExtras.getId());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void grupoVazioOuInativoNaoTransformaTodosEmAdministradores(boolean vazio) {
        var grupo = grupos.findAll().getFirst();
        if (vazio) grupo.setPermissoes(new LinkedHashSet<>()); else grupo.setAtivo(false);
        grupos.saveAndFlush(grupo);
        if (vazio) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM grupo_permissao_itens", Long.class)).isZero();
        assertThat(usuarios.buscarAdministradoresAtivos()).isEmpty();
    }

    @Test void novoPedidoGoogleNotificaTodosOsAdminsUmaVezSemEnviarEmailAosAdmins() {
        var pending = google.criarPendingGoogle("sub-nova", "nova@example.com", "Nova Pessoa", null);
        google.criarPendingGoogle("sub-nova", "nova@example.com", "Nova Pessoa", null);
        assertAvisos("nova@example.com");
        assertThat(emails.count()).isZero();
        assertThat(pending.getAprovado()).isFalse();
    }

    @Test void novoPedidoManualTambemNotificaTodosOsAdministradores() {
        usuarioService.criar(cadastro());
        assertAvisos("manual@example.com");
        assertThat(emails.count()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void aprovacaoEnfileiraUmEmailComLinkCorretoEMensagemParaIniciarAtividades(boolean viaGoogle) {
        Long id;
        if (viaGoogle) {
            id = google.criarPendingGoogle("sub-aprovada", "aprovada@example.com", "Pessoa Aprovada", null).getId();
            google.aprovar(id, administrador.getId(), cargo.getId(), Set.of(leitura.getIdPermissao()));
        } else {
            id = usuarioService.criar(cadastro()).getId();
            usuarioService.aprovarUsuario(id, administrador.getId(), cargo.getId(), Set.of(leitura.getIdPermissao()));
        }
        var mensagem = emails.findAll().getFirst();
        assertThat(emails.count()).isEqualTo(1);
        assertThat(mensagem.getDestinatario()).isEqualTo(viaGoogle ? "aprovada@example.com" : "manual@example.com");
        assertThat(mensagem.getAssunto()).contains("acesso ao CRM da Climbe foi aprovado");
        assertThat(mensagem.getMensagem()).contains("já pode iniciar suas atividades");
        assertThat(mensagem.getDestino()).isEqualTo(viaGoogle ? "/convite" : "/");
        assertThatThrownBy(() -> {
            if (viaGoogle) google.aprovar(id, administrador.getId(), cargo.getId(), Set.of(leitura.getIdPermissao()));
            else usuarioService.aprovarUsuario(id, administrador.getId(), cargo.getId(), Set.of(leitura.getIdPermissao()));
        }).isInstanceOf(RuntimeException.class);
        assertThat(emails.count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void recusaNaoEnviaEmailDeAprovacao(boolean viaGoogle) {
        if (viaGoogle) {
            var pending = google.criarPendingGoogle("sub-recusada", "recusada@example.com", "Pessoa", null);
            google.recusar(pending.getId(), administrador.getId());
        } else {
            usuarioService.recusarUsuario(usuarioService.criar(cadastro()).getId(), administrador.getId());
        }
        assertThat(emails.count()).isZero();
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackCancelaAvisosEAprovacaoSemDeixarEmailParaUmCadastroNaoAprovado() {
        long avisosAntes = avisos.count(), emailsAntes = emails.count(), pedidosAntes = solicitacoes.count();
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            prepararCatalogo();
            var pending = google.criarPendingGoogle("sub-rollback", "rollback@example.com", "Pessoa", null);
            google.aprovar(pending.getId(), administrador.getId(), cargo.getId(), Set.of(leitura.getIdPermissao()));
            assertThat(avisos.count()).isEqualTo(avisosAntes + 2);
            assertThat(emails.count()).isEqualTo(emailsAntes + 1);
            throw new IllegalStateException("Simular falha antes do commit");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simular falha antes do commit");
        assertThat(avisos.count()).isEqualTo(avisosAntes);
        assertThat(emails.count()).isEqualTo(emailsAntes);
        assertThat(solicitacoes.count()).isEqualTo(pedidosAntes);
    }

    private void assertAvisos(String destinatario) {
        assertThat(avisos.findAll()).hasSize(2).allSatisfy(aviso -> {
            assertThat(aviso.getTipo()).isEqualTo("SOLICITACAO_ACESSO");
            assertThat(aviso.getMensagem()).contains(destinatario);
            assertThat(aviso.getDestino()).isEqualTo("/aprovar-acesso");
            assertThat(aviso.getLida()).isFalse();
        }).extracting(aviso -> aviso.getUsuario().getId()).containsExactlyInAnyOrder(administrador.getId(), administradorComExtras.getId());
    }

    private UsuarioRequestDTO cadastro() {
        var dto = new UsuarioRequestDTO(); dto.setNomeCompleto("Pessoa Manual"); dto.setEmail("manual@example.com");
        dto.setCpf("12345678901"); dto.setContato("11999999999"); dto.setSenha("senha-segura"); dto.setCargoId(cargo.getId());
        return dto;
    }

    private Permissao permissao(String codigo) {
        var permissao = new Permissao(); permissao.setCodigo(codigo); permissao.setDescricao(codigo);
        return permissoes.save(permissao);
    }

    private Usuario usuario(String nome, String situacao, Set<Permissao> direitos) {
        var usuario = new Usuario(); usuario.setEmail(nome + "@example.com"); usuario.setNomeCompleto(nome);
        usuario.setContato("11999999999"); usuario.setSenhaHash("test"); usuario.setSituacao(situacao);
        usuario.getPermissoes().addAll(direitos); return usuarios.save(usuario);
    }
}
