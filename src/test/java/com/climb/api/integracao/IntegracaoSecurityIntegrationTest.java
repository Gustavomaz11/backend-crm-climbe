package com.climb.api.integracao;

import com.climb.api.model.*;
import com.climb.api.repository.*;
import com.climb.api.security.TokenSeguro;
import com.climb.api.service.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.mail.host=localhost", "logging.level.root=WARN", "debug=false", "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "spring.datasource.url=jdbc:h2:mem:integracoes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.integracoes.email.enabled=false", "app.notificacoes.email.enabled=false", "app.pipeline.cadencia.atraso-inicial-ms=86400000",
        "app.pipeline.task-reminders.weekly-cron=-", "app.pipeline.task-reminders.overdue-cron=-", "app.kanban.prazos.cron=-", "app.kanban.arquivamento.cron=-"})
@ActiveProfiles("test") @Transactional
class IntegracaoSecurityIntegrationTest {
    @Autowired IntegracaoService service;
    @Autowired IntegracaoTokens tokens;
    @Autowired IntegracaoClienteRepository repository;
    @Autowired IntegracaoCatalogo catalogo;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ContratoRepository contratos;
    @Autowired PipelineVendasFunilRepository funis;
    @Autowired PipelineVendasEtapaRepository etapas;
    @Autowired PipelineVendasNegocioRepository negocios;
    @Autowired IntegracaoEscopo escopo;
    @Autowired IntegracaoEntregaService entrega;
    @Autowired WebApplicationContext context;
    @Autowired SessaoAutenticacaoService sessoes;
    @MockitoBean RbacService rbac;
    @MockitoBean EmailService email;
    @MockitoBean CloudflareR2ArquivoStorageService storage;
    @MockitoBean ZapSignClient signatures;
    @MockitoBean IntegracaoAuditoriaService auditoriaDeRequisicoes;
    MockMvc mvc;
    @BeforeEach void setup() {
        when(rbac.temPermissao(anyLong(), any(PermissaoCodigo.class))).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test void entregaSegredoUmaVezMantemSomenteHashESeparaTiposDeToken() {
        var c = cliente(); var link = tokens.ativacao(c);
        assertThat(c.getSecretHash()).isNull();
        var credenciais = service.ativar(link);
        assertThat(repository.findById(c.getId()).orElseThrow().getSecretHash()).isEqualTo(TokenSeguro.hash(credenciais.client_secret()));
        assertThatThrownBy(() -> service.ativar(link)).isInstanceOf(ResponseStatusException.class);
        var token = service.autenticar(credenciais.client_id(), credenciais.client_secret());
        assertThat(token.expires_in()).isBetween(1L, 900L);
        assertThat(service.validarAcesso(token.access_token()).getId()).isEqualTo(c.getId());
        assertThatThrownBy(() -> service.validarAcesso(link)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.ativar(token.access_token())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.autenticar(credenciais.client_id(), "incorreto")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void suspensaoRotacaoRevogacaoEAlteracaoDePermissoesInvalidamTokens() {
        var c = cliente(); var segredo = service.ativar(tokens.ativacao(c));
        var primeiro = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        service.alterarStatus(c.getId(), "SUSPENSO", c.getCriadoPor());
        assertThatThrownBy(() -> service.validarAcesso(primeiro)).isInstanceOf(ResponseStatusException.class);
        service.alterarStatus(c.getId(), "ATIVO", c.getCriadoPor());
        assertThatThrownBy(() -> service.validarAcesso(primeiro)).isInstanceOf(ResponseStatusException.class);
        var segundo = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        service.salvar(c.getId(), cadastro(c.getEmpresaIds(), c.getExpiraEm()), c.getCriadoPor());
        assertThatThrownBy(() -> service.validarAcesso(segundo)).isInstanceOf(ResponseStatusException.class);
        var terceiro = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        service.rotacionar(c.getId(), c.getCriadoPor());
        assertThatThrownBy(() -> service.validarAcesso(terceiro)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.autenticar(segredo.client_id(), segredo.client_secret())).isInstanceOf(ResponseStatusException.class);
        var novo = service.ativar(tokens.ativacao(service.buscar(c.getId())));
        var quarto = service.autenticar(novo.client_id(), novo.client_secret()).access_token();
        service.alterarStatus(c.getId(), "REVOGADO", c.getCriadoPor());
        assertThatThrownBy(() -> service.validarAcesso(quarto)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.alterarStatus(c.getId(), "ATIVO", c.getCriadoPor())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void cadeiasReaisSeparamTokensEExigemModuloMetodoEOperacao() throws Exception {
        var c = cliente(); var segredo = service.ativar(tokens.ativacao(c));
        var token = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        mvc.perform(get("/api/integracoes/v1/juridico/contratos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/integracoes/v1/juridico/contratos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/integracoes/v1/comercial/propostas").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/integracoes/v1/juridico/contratos/1").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/integracoes/v1/juridico/contratos/1").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/integracoes").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        var user = usuarios.findById(c.getCriadoPor()).orElseThrow(); var humano = sessoes.criar(user).accessToken();
        mvc.perform(get("/api/integracoes/v1/juridico/contratos").header("Authorization", "Bearer " + humano)).andExpect(status().isUnauthorized());
        when(rbac.temPermissao(user.getId(), PermissaoCodigo.INTEGRACOES_GERENCIAR)).thenReturn(false);
        mvc.perform(get("/integracoes").header("Authorization", "Bearer " + humano)).andExpect(status().isForbidden());
    }

    @Test void filtroDeEmpresasRestringeGetSemRestringirDeleteEmTokenCombinado() throws Exception {
        var c = cliente(); var permitida = empresas.findById(c.getEmpresaIds().iterator().next()).orElseThrow();
        var outra = empresa("11.222.333/0001-81"); var user = usuarios.findById(c.getCriadoPor()).orElseThrow();
        contrato(permitida, user); contrato(outra, user);
        var cadastro = new IntegracaoDTO.Cadastro(c.getNome(), c.getCnpj(), c.getResponsavel(), c.getEmail(), IntegracaoModulo.JURIDICO,
                Set.of("GET", "DELETE"), Set.of("juridico.contratos.listar", "juridico.contratos.ler", "juridico.contratos.excluir"), c.getEmpresaIds(), c.getExpiraEm(), 120);
        service.salvar(c.getId(), cadastro, user.getId());
        var segredo = service.ativar(tokens.ativacao(service.buscar(c.getId()))); var token = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        mvc.perform(get("/api/integracoes/v1/juridico/contratos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].empresaId").value(permitida.getIdEmpresa()));
        var externo = contratos.findByEmpresa_IdEmpresaOrderByIdContratoDesc(outra.getIdEmpresa()).getFirst();
        mvc.perform(get("/api/integracoes/v1/juridico/contratos/" + externo.getIdContrato()).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/integracoes/v1/juridico/contratos/" + externo.getIdContrato()).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertThat(contratos.existsById(externo.getIdContrato())).isFalse();
    }

    @Test void limiteCompartilhadoNaoPodeSerContornadoEmitindoOutroToken() throws Exception {
        var c = cliente(); c.setLimitePorMinuto(2); repository.saveAndFlush(c);
        var segredo = service.ativar(tokens.ativacao(c)); var token = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        mvc.perform(get("/api/integracoes/v1/juridico/contratos").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/integracoes/v1/juridico/contratos").header("Authorization", "Bearer " + token)).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        assertThatThrownBy(() -> service.autenticar(segredo.client_id(), segredo.client_secret())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void fluxoPublicoOAuthUsaBasicESegredoNuncaRetornaNaAdministracao() throws Exception {
        var c = cliente();
        var ativacao = mvc.perform(post("/api/integracoes/ativar").contentType("application/json")
                .content("{\"token\":\"" + tokens.ativacao(c) + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        var json = com.google.gson.JsonParser.parseString(ativacao).getAsJsonObject();
        String basic = Base64.getEncoder().encodeToString((json.get("client_id").getAsString() + ":" + json.get("client_secret").getAsString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(post("/api/integracoes/oauth/token").contentType("application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic).param("grant_type", "client_credentials"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/integracoes/oauth/token").contentType("application/x-www-form-urlencoded")
                .header("Authorization", "Basic invalido").param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));
        var humano = sessoes.criar(usuarios.findById(c.getCriadoPor()).orElseThrow()).accessToken();
        mvc.perform(get("/integracoes").header("Authorization", "Bearer " + humano)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist()).andExpect(jsonPath("$[0].secretHash").doesNotExist())
                .andExpect(jsonPath("$[0].ativacaoNonce").doesNotExist());
    }

    @Test void gravacaoComercialDispensaEmpresasNoCadastroEIgnoraCamposDeAprovacaoEUsuario() throws Exception {
        var c = cliente();
        Long empresaId = c.getEmpresaIds().iterator().next();
        var input = new IntegracaoDTO.Cadastro(c.getNome(), c.getCnpj(), c.getResponsavel(), c.getEmail(), IntegracaoModulo.COMERCIAL,
                Set.of("POST", "PATCH"), Set.of("comercial.propostas.criar", "comercial.propostas.valuation"), null, c.getExpiraEm(), 120);
        assertThat(service.salvar(c.getId(), input, c.getCriadoPor()).empresaIds()).isEmpty();
        var segredo = service.ativar(tokens.ativacao(service.buscar(c.getId())));
        String token = service.autenticar(segredo.client_id(), segredo.client_secret()).access_token();
        var resultado = mvc.perform(post("/api/integracoes/v1/comercial/propostas").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"empresaId\":" + empresaId + ",\"valuation\":100000,\"status\":\"APROVADA\",\"usuarioId\":999999}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        Long id = com.google.gson.JsonParser.parseString(resultado).getAsJsonObject().get("id").getAsLong();
        mvc.perform(patch("/api/integracoes/v1/comercial/propostas/" + id + "/valuation").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"valuation\":125000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valuation").value(125000)).andExpect(jsonPath("$.status").value("PENDENTE"));
        var outra = empresa("11.222.333/0001-81");
        mvc.perform(post("/api/integracoes/v1/comercial/propostas").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"empresaId\":" + outra.getIdEmpresa() + ",\"valuation\":100000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.empresaId").value(outra.getIdEmpresa()));
        mvc.perform(get("/api/integracoes/v1/comercial/propostas").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test void cadastroAdminAceitaEmpresaIdsOmitidoSemGetEExigeEmpresasComGet() throws Exception {
        var c = cliente(); var humano = sessoes.criar(usuarios.findById(c.getCriadoPor()).orElseThrow()).accessToken();
        var dados = new LinkedHashMap<String, Object>(Map.of("nome", c.getNome(), "cnpj", c.getCnpj(),
                "responsavel", c.getResponsavel(), "email", c.getEmail(), "modulo", "COMERCIAL",
                "metodos", List.of("POST"), "operacoes", List.of("comercial.propostas.criar"),
                "expiraEm", c.getExpiraEm().toString(), "limitePorMinuto", 120));
        var gson = new com.google.gson.Gson();
        mvc.perform(post("/integracoes").header("Authorization", "Bearer " + humano).contentType("application/json").content(gson.toJson(dados)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.empresaIds").isEmpty());
        dados.put("metodos", List.of("GET")); dados.put("operacoes", List.of("comercial.propostas.listar"));
        mvc.perform(post("/integracoes").header("Authorization", "Bearer " + humano).contentType("application/json").content(gson.toJson(dados)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Selecione ao menos uma empresa autorizada para GET."));
    }

    @Test void filaNaoSobrescreveReenvioNovoEReagendaFalhaSemPersistirSegredo() {
        var c = cliente(); var envio = entrega.reservar(); assertThat(envio).isNotNull();
        assertThat(envio.cliente().getId()).isEqualTo(c.getId());
        entrega.concluir(envio, false); assertThat(service.buscar(c.getId()).getEmailStatus()).isEqualTo("FALHOU");
        service.reenviar(c.getId(), c.getCriadoPor()); entrega.concluir(envio, true);
        assertThat(service.buscar(c.getId()).getEmailStatus()).isEqualTo("PENDENTE");
        assertThat(service.buscar(c.getId()).getSecretHash()).isNull();
        var novo = entrega.reservar(); entrega.concluir(novo, true);
        assertThat(service.buscar(c.getId()).getEmailStatus()).isEqualTo("ENVIADO");
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duasAtivacoesConcorrentesGeramSomenteUmSegredo() throws Exception {
        var c = cliente(); c.setEmailProximaTentativa(null); repository.saveAndFlush(c);
        String link = tokens.ativacao(c);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> ativar = () -> { inicio.await(); try { service.ativar(link); return true; } catch (ResponseStatusException e) { return false; } };
            var a = pool.submit(ativar); var b = pool.submit(ativar); inicio.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emissoesSimultaneasRespeitamOMesmoLimitePersistido() throws Exception {
        var c = cliente(); c.setLimitePorMinuto(1); c.setEmailProximaTentativa(null); repository.saveAndFlush(c);
        var credenciais = service.ativar(tokens.ativacao(c));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> emitir = () -> { inicio.await(); try {
                service.autenticar(credenciais.client_id(), credenciais.client_secret()); return true;
            } catch (ResponseStatusException e) { assertThat(e.getStatusCode().value()).isEqualTo(429); return false; } };
            var tarefas = new ArrayList<Future<Boolean>>(); for (int i = 0; i < 4; i++) tarefas.add(pool.submit(emitir)); inicio.countDown();
            int aceitos = 0; for (var tarefa : tarefas) if (tarefa.get(15, TimeUnit.SECONDS)) aceitos++;
            assertThat(aceitos).isEqualTo(1);
        }
    }

    @Test void rejeitaOperacoesDeOutroModuloEValidadeExpirada() {
        var c = cliente();
        var invalido = new IntegracaoDTO.Cadastro("Exemplo", c.getCnpj(), "Responsável", c.getEmail(), IntegracaoModulo.JURIDICO,
                Set.of("GET"), Set.of("comercial.propostas.listar"), c.getEmpresaIds(), c.getExpiraEm(), 120);
        assertThatThrownBy(() -> service.salvar(null, invalido, c.getCriadoPor())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.salvar(null, cadastro(c.getEmpresaIds(), Instant.now().minusSeconds(5)), c.getCriadoPor())).isInstanceOf(ResponseStatusException.class);
    }
    @Test void leadDaIntegracaoSempreEntraNaListaDeLeadsDoFunilPreVendas() throws Exception {
        var c = cliente(); Long empresaId = c.getEmpresaIds().iterator().next();
        var vendas = funil("VENDAS_TESTE", "VENDAS", 1);
        var pre = funil("PRE_VENDAS", "PRE_VENDAS", 2);
        var outraEtapa = etapa(pre, "LEAD_CONECTADO", 1);
        var lista = etapa(pre, "LISTA_LEADS", 99);
        String token = tokenLead(c);
        String body = new com.google.gson.Gson().toJson(Map.of("empresaId", empresaId, "nomeContato", "Lead da plataforma",
                "responsavelId", c.getCriadoPor(), "origemNegocio", "Integração", "estrategiaComercial", "Contato direto",
                "funilId", vendas.getIdFunil(), "etapaId", outraEtapa.getIdEtapa()));
        var resposta = mvc.perform(post("/api/integracoes/v1/comercial/pre-vendas/leads").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.funilId").value(pre.getIdFunil()))
                .andExpect(jsonPath("$.etapaId").value(lista.getIdEtapa())).andExpect(jsonPath("$.resultado").value("ABERTO"))
                .andReturn().getResponse().getContentAsString();
        Long id = com.google.gson.JsonParser.parseString(resposta).getAsJsonObject().get("id").getAsLong();
        assertThat(negocios.findById(id).orElseThrow().getFunil().getCodigo()).isEqualTo("PRE_VENDAS");
        assertThat(negocios.findById(id).orElseThrow().getEtapa().getCodigo()).isEqualTo("LISTA_LEADS");
    }
    @Test void operacaoDeLeadsExigePermissaoPropriaENaoUsaFunilDeVendasSePreVendasNaoExistir() throws Exception {
        var c = cliente(); Long empresaId = c.getEmpresaIds().iterator().next();
        var configuracao = new IntegracaoDTO.Cadastro(c.getNome(), c.getCnpj(), c.getResponsavel(), c.getEmail(), IntegracaoModulo.COMERCIAL,
                Set.of("POST"), Set.of("comercial.negocios.criar"), Set.of(), c.getExpiraEm(), 120);
        service.salvar(c.getId(), configuracao, c.getCriadoPor());
        var credenciais = service.ativar(tokens.ativacao(service.buscar(c.getId())));
        var token = service.autenticar(credenciais.client_id(), credenciais.client_secret()).access_token();
        String body = new com.google.gson.Gson().toJson(Map.of("empresaId", empresaId, "nomeContato", "Lead da plataforma",
                "responsavelId", c.getCriadoPor(), "origemNegocio", "Integração", "estrategiaComercial", "Contato direto"));
        mvc.perform(post("/api/integracoes/v1/comercial/pre-vendas/leads").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        c = cliente(); token = tokenLead(c); funil("VENDAS_TESTE", "VENDAS", 1);
        long antes = negocios.count();
        mvc.perform(post("/api/integracoes/v1/comercial/pre-vendas/leads").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        assertThat(negocios.count()).isEqualTo(antes);
    }
    private String tokenLead(IntegracaoCliente c) {
        var cadastro = new IntegracaoDTO.Cadastro(c.getNome(), c.getCnpj(), c.getResponsavel(), c.getEmail(), IntegracaoModulo.COMERCIAL,
                Set.of("POST"), Set.of("comercial.prevendas.leads.criar"), Set.of(), c.getExpiraEm(), 120);
        service.salvar(c.getId(), cadastro, c.getCriadoPor());
        var credenciais = service.ativar(tokens.ativacao(service.buscar(c.getId())));
        return service.autenticar(credenciais.client_id(), credenciais.client_secret()).access_token();
    }
    private PipelineVendasFunil funil(String codigo, String tipo, int posicao) {
        var f = new PipelineVendasFunil(); f.setCodigo(codigo); f.setTipo(tipo); f.setNome("PRE_VENDAS".equals(tipo) ? "Pré-vendas" : "Vendas");
        f.setEstrategia("Todas as estratégias"); f.setPosicao(posicao); return funis.saveAndFlush(f);
    }
    private PipelineVendasEtapa etapa(PipelineVendasFunil funil, String codigo, int posicao) {
        var e = new PipelineVendasEtapa(); e.setFunil(funil); e.setCodigo(codigo); e.setNome(codigo); e.setPosicao(posicao);
        e.setResultado(com.climb.api.model.enums.PipelineVendasResultado.ABERTO); return etapas.saveAndFlush(e);
    }
    private IntegracaoCliente cliente() {
        var user = new Usuario(); user.setNomeCompleto("Teste integração"); user.setContato("85999999999");
        user.setEmail(UUID.randomUUID() + "@test.local"); user.setSituacao("ATIVO"); user.setSenhaHash("hash-teste"); usuarios.saveAndFlush(user);
        var e = empresa("04.252.011/0001-10");
        var criado = service.salvar(null, cadastro(Set.of(e.getIdEmpresa()), Instant.now().plusSeconds(86400)), user.getId());
        return repository.findById(criado.id()).orElseThrow();
    }
    private IntegracaoDTO.Cadastro cadastro(Set<Long> ids, Instant validade) {
        return new IntegracaoDTO.Cadastro("Plataforma exemplo", "04252011000110", "Representante exemplo", "integracao@example.com", IntegracaoModulo.JURIDICO,
                Set.of("GET"), Set.of("juridico.contratos.listar"), ids, validade, 120);
    }
    private Empresa empresa(String cnpj) {
        return empresas.findByCnpj(cnpj).orElseGet(() -> { var e = new Empresa(); e.setNomeFantasia("Empresa exemplo"); e.setRazaoSocial("Empresa exemplo LTDA"); e.setCnpj(cnpj); return empresas.saveAndFlush(e); });
    }
    private void contrato(Empresa e, Usuario u) {
        var c = new Contrato(); c.setEmpresa(e); c.setEmpresaNomeFantasia(e.getNomeFantasia()); c.setUsuario(u); c.setStatus("PENDENTE"); contratos.saveAndFlush(c);
    }
}
