package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false", "spring.mail.host=localhost",
    "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "logging.level.org.springframework=WARN",
    "app.pipeline.cadencia.atraso-inicial-ms=86400000",
    "app.pipeline.task-reminders.weekly-cron=-", "app.pipeline.task-reminders.overdue-cron=-"
})
@ActiveProfiles("test") @Transactional
class PipelinePreVendasIntegrationTest {
    @Autowired PipelineVendasService vendas;
    @Autowired PipelineCampanhaService campanhas;
    @Autowired PipelineTarefaService tarefas;
    @Autowired PipelineCadastroService cadastros;
    @Autowired PipelineVendasNegocioRepository negocios;
    @Autowired PipelineVendasTarefaRepository tarefaRepository;
    @Autowired PipelineVendasFunilRepository funis;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired PessoaClienteRepository pessoas;
    @MockitoBean RbacService rbac;

    @Test
    void pessoaPodeTerDoisLeadsEConversaoPreservaOrigemSemDuplicarVenda() {
        var f = preparar();
        var tag = cadastros.salvarTag(f.usuario.getId(), null, "Prioridade 3", "#aabbcc", true);
        var campo = cadastros.salvarCampo(f.usuario.getId(), null, "Perfil", "TEXTO", "TODOS", null, true);
        var campanha = campanha(f, "Recuperação Setembro");
        var dto = dados(f, campanha.id(), f.pre.getEtapas().getFirst().getIdEtapa(), Set.of(tag.getId()), Map.of(campo.getId(), "Consultoria"));
        var lead = vendas.criar(f.usuario.getId(), dto);
        var outro = vendas.criar(f.usuario.getId(), dados(f, null, f.pre.getEtapas().getFirst().getIdEtapa(), Set.of(), Map.of()));
        assertNotEquals(lead.id(), outro.id()); assertEquals(lead.pessoaId(), outro.pessoaId());
        var ganho = vendas.ganharPreVenda(lead.id(), f.usuario.getId(), f.venda.getIdFunil(), f.usuario.getId());
        var repetido = vendas.ganharPreVenda(lead.id(), f.usuario.getId(), f.venda.getIdFunil(), f.usuario.getId());
        assertNotNull(ganho.negocioGeradoId()); assertEquals(ganho.negocioGeradoId(), repetido.negocioGeradoId());
        var negocio = vendas.buscar(ganho.negocioGeradoId(), f.usuario.getId());
        assertEquals("DIAGNOSTICO", negocio.etapaCodigo()); assertEquals(PipelineVendasResultado.ABERTO, negocio.resultado());
        assertEquals(lead.id(), negocio.preVendaOrigemId()); assertEquals(lead.pessoaId(), negocio.pessoaId());
        assertEquals(campanha.id(), negocio.campanhaOrigemId()); assertEquals("Recuperação", negocio.estrategiaComercial());
        assertTrue(negocio.tags().stream().anyMatch(t -> t.getId().equals(tag.getId())));
        assertEquals("Consultoria", negocio.campos().get(campo.getId()));
        assertEquals(1, negocios.findDashboardNegocios(null, null, null, null, null, null, null, null, null).size());
    }

    @Test
    void fluxoPorEtapaAvancaComCancelamentoEExigeReinicioAposRetorno() {
        var f = preparar(); var campanha = campanha(f, "Recuperação tarefas");
        var tentativa = f.pre.getEtapas().getFirst(); var conectado = f.pre.getEtapas().get(1);
        var lead = vendas.criar(f.usuario.getId(), dados(f, campanha.id(), tentativa.getIdEtapa(), Set.of(), Map.of()));
        var primeira = tarefaRepository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(lead.id());
        assertEquals(1, primeira.size());
        tarefas.cancelar(primeira.getFirst().getIdTarefa(), f.usuario.getId(), "SEM_CANAL_CONTATO", "Sem telefone");
        var lista = tarefaRepository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(lead.id());
        assertEquals(2, lista.size());
        assertEquals(1, lista.stream().filter(t -> t.getStatus() == PipelineTarefaStatus.CANCELADA).count());
        vendas.mover(lead.id(), f.usuario.getId(), conectado.getIdEtapa(), null, null);
        assertTrue(lista.stream().allMatch(t -> t.getStatus() == PipelineTarefaStatus.CANCELADA));
        vendas.mover(lead.id(), f.usuario.getId(), tentativa.getIdEtapa(), null, null);
        assertEquals(2, tarefaRepository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(lead.id()).size());
        vendas.reiniciarCadencia(lead.id(), f.usuario.getId());
        assertEquals(3, tarefaRepository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(lead.id()).size());
        assertEquals(2, tarefaRepository.findCancelamentos(null, null, campanha.id(), null).size());
    }

    @Test
    void conversaoRespeitaCamposObrigatoriosDaEtapaDeDestino() {
        var f = preparar();
        f.venda.getEtapas().getFirst().setCamposObrigatorios(List.of("email"));
        var lead = vendas.criar(f.usuario.getId(), dados(f, null, f.pre.getEtapas().getFirst().getIdEtapa(), Set.of(), Map.of()));
        var erro = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> vendas.ganharPreVenda(lead.id(), f.usuario.getId(), f.venda.getIdFunil(), f.usuario.getId()));
        assertTrue(erro.getReason().contains("email"));
        assertTrue(negocios.findByPreVendaOrigemIdNegocio(lead.id()).isEmpty());
    }

    @Test
    void planoNovoPreservaExecucaoDaVersaoAnterior() {
        var f = preparar(); var campanha = campanha(f, "Plano versionado");
        var lead = vendas.criar(f.usuario.getId(), dados(f, campanha.id(), f.pre.getEtapas().getFirst().getIdEtapa(), Set.of(), Map.of()));
        campanhas.alterarAtivacao(campanha.id(), f.usuario.getId(), false);
        var novasEtapas = List.of(
            new PipelineCadenciaEtapaRequestDTO(PipelineCadenciaTipo.TAREFA, "Plano novo", "Nova instrução", "Mensagem", PipelineTarefaPrioridade.MEDIA, 0, 0, null, "TENTATIVA_CONTATO"),
            new PipelineCadenciaEtapaRequestDTO(PipelineCadenciaTipo.ENCERRAR, null, null, null, null, 0, null, null, "TENTATIVA_CONTATO"));
        campanhas.atualizar(campanha.id(), f.usuario.getId(), new PipelineCampanhaRequestDTO(campanha.nome(), "Recuperação", null,
            Set.of(lead.id()), Set.of(f.usuario.getId()), Set.of(1,2,3,4,5,6,7), Set.of(), novasEtapas, true));
        var primeira = tarefaRepository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(lead.id()).getFirst();
        tarefas.alterarStatus(primeira.getIdTarefa(), f.usuario.getId(), PipelineTarefaStatus.CONCLUIDA);
        var lista = tarefaRepository.findByNegocioIdNegocioOrderByPrazoAscCriadoEmDesc(lead.id());
        assertEquals(2, lista.size());
        assertTrue(lista.stream().anyMatch(t -> "Segundo contato".equals(t.getTitulo())));
        assertTrue(lista.stream().noneMatch(t -> "Plano novo".equals(t.getTitulo())));
    }

    @Test
    void loteCriaTrintaLeadsDistintosDaMesmaPessoa() {
        var f = preparar();
        var dto = dados(f, null, f.pre.getEtapas().getFirst().getIdEtapa(), Set.of(), Map.of());
        var lote = vendas.criarLote(f.usuario.getId(), Collections.nCopies(30, dto));
        assertEquals(30, lote.size());
        assertEquals(30, lote.stream().map(PipelineNegocioResponseDTO::id).distinct().count());
        assertTrue(lote.stream().allMatch(l -> l.pessoaId().equals(f.pessoa.getIdPessoa())));
        assertEquals(30, vendas.buscarBoard(f.usuario.getId(), f.pre.getIdFunil()).etapas().stream().mapToInt(e -> e.negocios().size()).sum());
    }

    @Test
    void cadastroExigePermissaoEValidaTipoEEscopoDoCampo() {
        var f = preparar();
        when(rbac.temPermissao(f.usuario.getId(), PermissaoCodigo.COMERCIAL_CADASTROS_GERENCIAR)).thenReturn(false);
        var negado = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> cadastros.salvarTag(f.usuario.getId(), null, "Restrita", "#112233", true));
        assertEquals(403, negado.getStatusCode().value());
        when(rbac.temPermissao(f.usuario.getId(), PermissaoCodigo.COMERCIAL_CADASTROS_GERENCIAR)).thenReturn(true);
        var numero = cadastros.salvarCampo(f.usuario.getId(), null, "Quantidade", "NUMERO", "TODOS", null, true);
        var exclusivo = cadastros.salvarCampo(f.usuario.getId(), null, "Somente venda", "TEXTO", "VENDAS", null, true);
        var negocio = new PipelineVendasNegocio(); negocio.setFunil(f.pre); negocio.setEmpresa(f.empresa);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> cadastros.aplicar(negocio, dados(f, null, null, Set.of(), Map.of(numero.getId(), "inválido"))));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> cadastros.aplicar(negocio, dados(f, null, null, Set.of(), Map.of(exclusivo.getId(), "valor"))));
    }

    private PipelineCampanhaResponseDTO campanha(Fixture f, String nome) {
        return campanhas.criar(f.usuario.getId(), new PipelineCampanhaRequestDTO(nome, "Recuperação", null,
            Set.of(), Set.of(f.usuario.getId()), Set.of(1, 2, 3, 4, 5, 6, 7), Set.of(), List.of(
                new PipelineCadenciaEtapaRequestDTO(PipelineCadenciaTipo.TAREFA, "Primeiro contato", "Conversar", "Ligação", PipelineTarefaPrioridade.MEDIA, 0, 0, null, "TENTATIVA_CONTATO"),
                new PipelineCadenciaEtapaRequestDTO(PipelineCadenciaTipo.TAREFA, "Segundo contato", "Retornar", "E-mail", PipelineTarefaPrioridade.MEDIA, 0, 0, null, "TENTATIVA_CONTATO"),
                new PipelineCadenciaEtapaRequestDTO(PipelineCadenciaTipo.ENCERRAR, null, null, null, null, 0, null, null, "TENTATIVA_CONTATO")
            ), true));
    }

    private PipelineNegocioRequestDTO dados(Fixture f, Long campanha, Long etapa, Set<Long> tags, Map<Long, String> campos) {
        return new PipelineNegocioRequestDTO(f.pre.getIdFunil(), f.empresa.getIdEmpresa(), "Empresa teste", "Maria", "", "",
            f.usuario.getId(), etapa, null, "Outbound", "Ativa", "", null, "Histórico inicial", List.of(), false, null,
            f.pessoa.getIdPessoa(), campanha, tags, campos);
    }

    private Fixture preparar() {
        when(rbac.temPermissao(anyLong(), any())).thenReturn(true);
        Usuario usuario = new Usuario(); usuario.setNomeCompleto("Operador teste"); usuario.setCpf("12345678900");
        usuario.setEmail("operador@example.test"); usuario.setContato("11999999999"); usuario.setSenhaHash("test"); usuario.setSituacao("ATIVO"); usuarios.saveAndFlush(usuario);
        Empresa empresa = new Empresa(); empresa.setRazaoSocial("Empresa teste"); empresa.setNomeFantasia("Empresa teste"); empresa.setCnpj("11.222.333/0001-81"); empresas.saveAndFlush(empresa);
        PessoaCliente pessoa = new PessoaCliente(); pessoa.setNome("Maria"); pessoa.setEmpresas(new LinkedHashSet<>(Set.of(empresa)));
        pessoa.setCriadoEm(LocalDateTime.now()); pessoa.setAtualizadoEm(LocalDateTime.now()); pessoas.saveAndFlush(pessoa);
        var pre = funil("PRE_VENDAS", 1, "TENTATIVA_CONTATO", "LEAD_CONECTADO");
        var venda = funil("VENDAS", 2, "DIAGNOSTICO");
        return new Fixture(usuario, empresa, pessoa, pre, venda);
    }

    private PipelineVendasFunil funil(String tipo, int posicao, String... codigos) {
        var funil = new PipelineVendasFunil(); funil.setCodigo(tipo); funil.setNome(tipo); funil.setTipo(tipo);
        funil.setEstrategia("Todas"); funil.setPosicao(posicao);
        for (int i = 0; i < codigos.length; i++) {
            var etapa = new PipelineVendasEtapa(); etapa.setFunil(funil); etapa.setCodigo(codigos[i]); etapa.setNome(codigos[i]);
            etapa.setPosicao(i + 1); etapa.setResultado(PipelineVendasResultado.ABERTO); funil.getEtapas().add(etapa);
        }
        return funis.saveAndFlush(funil);
    }
    private record Fixture(Usuario usuario, Empresa empresa, PessoaCliente pessoa, PipelineVendasFunil pre, PipelineVendasFunil venda) {}
}
