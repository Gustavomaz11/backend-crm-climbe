package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.mail.host=localhost", "app.kanban.arquivamento.cron=-"})
@ActiveProfiles("test")
@Transactional
class KanbanArquivamentoIntegrationTest {
    @Autowired private EntityManager em;
    @Autowired private KanbanArquivamentoAutomaticoService automatico;
    @Autowired private ContratoKanbanService kanban;
    @Autowired private PipelineTarefaService atividades;
    @Autowired private PipelineVendasService vendas;
    @Autowired private ContratoService contratos;
    @Autowired private ReuniaoService reunioes;
    @MockitoBean private RbacService rbac;
    @MockitoBean private EmailService emails;
    @MockitoBean private TarefaNotificacaoService notificacoes;
    private final LocalDateTime agora = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Test
    void arquivaTodosOsTiposSomenteAposQuinzeDiasESemRepetirConclusoes() {
        var f = preparar();
        assertEquals(0, automatico.arquivarVencidos(agora.minusSeconds(1)));
        assertEquals(5, automatico.arquivarVencidos(agora));
        em.clear();
        var tarefa = em.find(ContratoKanbanTask.class, f.tarefa.getIdTask());
        assertEquals(f.raia.getIdRaia(), tarefa.getRaia().getIdRaia());
        assertEquals(agora.minusDays(15), tarefa.getConcluidaEm());
        assertTrue(tarefa.estaArquivado());
        assertEquals(0, automatico.arquivarVencidos(agora.plusDays(1)));
        assertEquals(0, kanban.buscarBoard(f.contrato.getIdContrato(), f.usuario.getId()).raias().get(0).tasks().size());
        assertEquals(1, kanban.listarArquivados(f.contrato.getIdContrato(), f.usuario.getId()).size());
        assertTrue(atividades.listarDoNegocio(f.negocio.getIdNegocio(), f.usuario.getId()).isEmpty());
        assertEquals(1, atividades.listarArquivados(f.usuario.getId(), f.negocio.getIdNegocio(), null).size());
        assertTrue(vendas.buscarBoard(f.usuario.getId(), f.funil.getIdFunil()).etapas().get(0).negocios().isEmpty());
        assertEquals(1, vendas.listarArquivados(f.usuario.getId(), f.funil.getIdFunil()).size());
        assertEquals(1, contratos.listarArquivados(f.usuario.getId()).size());
        assertEquals(1, reunioes.listarArquivados(f.usuario.getId()).size());
        verifyNoInteractions(notificacoes, emails);
    }

    @Test
    void desarquivamentoReiniciaPrazoSemAlterarDataOriginalDaConclusao() {
        var f = preparar();
        automatico.arquivarVencidos(agora);
        em.clear();
        var tarefa = em.find(ContratoKanbanTask.class, f.tarefa.getIdTask());
        tarefa.alterarArquivamento(false, agora);
        em.flush();
        assertEquals(agora.plusDays(15), tarefa.getArquivarApos());
        assertEquals(agora.minusDays(15), tarefa.getConcluidaEm());
        assertEquals(1, kanban.buscarBoard(f.contrato.getIdContrato(), f.usuario.getId()).raias().get(0).tasks().size());
        assertEquals(0, automatico.arquivarVencidos(agora.plusDays(15).minusSeconds(1)));
        assertEquals(1, automatico.arquivarVencidos(agora.plusDays(15)));
        verifyNoInteractions(notificacoes, emails);
    }

    @Test
    void permiteArquivarManualmenteEPreservaAcessoAoContratoOperacional() {
        var f = preparar();
        kanban.alterarArquivamento(f.contrato.getIdContrato(), f.tarefa.getIdTask(), f.usuario.getId(), true);
        contratos.alterarArquivamento(f.contrato.getIdContrato(), f.usuario.getId(), true);
        em.flush();
        assertTrue(f.tarefa.estaArquivado());
        assertTrue(f.contrato.estaArquivado());
        assertNotNull(kanban.buscarBoard(f.contrato.getIdContrato(), f.usuario.getId()));
        kanban.alterarArquivamento(f.contrato.getIdContrato(), f.tarefa.getIdTask(), f.usuario.getId(), false);
        assertEquals(1, kanban.buscarBoard(f.contrato.getIdContrato(), f.usuario.getId()).raias().get(0).tasks().size());
        verifyNoInteractions(notificacoes, emails);
    }

    @Test
    void negaArquivamentoSemPermissao() {
        var f = preparar();
        when(rbac.temPermissao(f.usuario.getId(), PermissaoCodigo.COMERCIAL_TAREFA_EDITAR)).thenReturn(false);
        var erro = assertThrows(ResponseStatusException.class,
                () -> atividades.alterarArquivamento(f.atividade.getIdTarefa(), f.usuario.getId(), true));
        assertEquals(403, erro.getStatusCode().value());
        assertFalse(f.atividade.estaArquivado());
    }

    @Test
    void naoArquivaItensQueSaemDasColunasFinaisMesmoComPrazoAntigo() {
        var f = preparar();
        em.createNativeQuery("update contrato_kanban_raias set conclui_tarefas = false").executeUpdate();
        em.createNativeQuery("update pipeline_vendas_tarefas set status = 'PENDENTE'").executeUpdate();
        em.createNativeQuery("update pipeline_vendas_negocios set resultado = 'ABERTO'").executeUpdate();
        em.createNativeQuery("update contratos set etapa_preparacao = 'EM_ANDAMENTO', status = 'PENDENTE'").executeUpdate();
        em.createNativeQuery("update reunioes set status = 'AGENDADA'").executeUpdate();
        em.clear();
        assertEquals(0, automatico.arquivarVencidos(agora.plusDays(1)));
        var tarefa = em.find(PipelineVendasTarefa.class, f.atividade.getIdTarefa());
        var erro = assertThrows(ResponseStatusException.class, () -> tarefa.alterarArquivamento(true, agora));
        assertEquals(400, erro.getStatusCode().value());
        assertFalse(tarefa.estaArquivado());
    }

    @Test
    void tambemArquivaPerdasDoPipeline() {
        var f = preparar();
        f.negocio.setResultado(PipelineVendasResultado.PERDIDO);
        em.flush();
        assertEquals(5, automatico.arquivarVencidos(agora));
        em.clear();
        assertTrue(em.find(PipelineVendasNegocio.class, f.negocio.getIdNegocio()).estaArquivado());
    }

    private Fixture preparar() {
        when(rbac.temPermissao(anyLong(), any())).thenReturn(true);
        var usuario = new Usuario(); usuario.setNomeCompleto("Ana"); usuario.setEmail("ana-arquivo@test.com");
        usuario.setContato("11999999999"); usuario.setSenhaHash("hash"); usuario.setSituacao("ATIVO"); em.persist(usuario);
        var empresa = new Empresa(); empresa.setNomeFantasia("Empresa"); empresa.setRazaoSocial("Empresa LTDA"); empresa.setCnpj("11222333000181"); em.persist(empresa);
        var contrato = new Contrato(); contrato.setUsuario(usuario); contrato.setResponsavel(usuario); contrato.setEmpresa(empresa);
        contrato.setEmpresaNomeFantasia("Empresa"); contrato.setStatus("APROVADO"); contrato.setEtapaPreparacao(ContratoPreparacaoEtapa.CONCLUIDO);
        contrato.setEquipeConfigurada(true);
        var raia = new ContratoKanbanRaia(); raia.setContrato(contrato); raia.setTitulo("Concluído"); raia.setConcluiTarefas(true);
        var tarefa = new ContratoKanbanTask(); tarefa.setContrato(contrato); tarefa.setRaia(raia); tarefa.setTitulo("Entrega final");
        tarefa.setResponsaveis(List.of(usuario)); tarefa.setConcluidaEm(agora.minusDays(15));
        var funil = new PipelineVendasFunil(); funil.setCodigo("ARQUIVO"); funil.setNome("Vendas"); funil.setAtivo(true);
        funil.setPosicao(0); funil.setEstrategia("Venda direta"); em.persist(funil);
        var etapa = new PipelineVendasEtapa(); etapa.setFunil(funil); etapa.setCodigo("GANHO"); etapa.setNome("Ganho");
        etapa.setResultado(PipelineVendasResultado.GANHO); etapa.setPosicao(0); em.persist(etapa);
        var negocio = new PipelineVendasNegocio(); negocio.setFunil(funil); negocio.setEtapa(etapa); negocio.setEmpresa(empresa);
        negocio.setNomeEmpresa("Empresa"); negocio.setNomeContato("Ana"); negocio.setResponsavel(usuario); negocio.setCriadoPor(usuario);
        negocio.setOrigemNegocio("Indicação"); negocio.setEstrategiaComercial("Direta"); negocio.setServicoInteresse("Consultoria"); negocio.setResultado(PipelineVendasResultado.GANHO);
        negocio.setCriadoEm(agora.minusDays(15)); negocio.setUltimaMovimentacaoEm(agora.minusDays(15));
        var atividade = new PipelineVendasTarefa(); atividade.setNegocio(negocio); atividade.setTitulo("Visita concluída");
        atividade.setResponsavel(usuario); atividade.setCriadoPor(usuario); atividade.setTipo("Visita");
        atividade.setPrioridade(PipelineTarefaPrioridade.MEDIA); atividade.setStatus(PipelineTarefaStatus.CONCLUIDA);
        var reuniao = new Reuniao(); reuniao.setEmpresa(empresa); reuniao.setTitulo("Reunião realizada"); reuniao.setStatus("REALIZADA");
        for (var item : List.of(contrato, tarefa, negocio, atividade, reuniao)) item.sincronizarPrazoArquivamento(agora.minusDays(15));
        em.persist(contrato); em.persist(raia); em.persist(tarefa); em.persist(negocio); em.persist(atividade); em.persist(reuniao);
        var participante = new ParticipanteReuniao(); participante.setReuniao(reuniao); participante.setUsuario(usuario); em.persist(participante);
        em.flush();
        return new Fixture(usuario, contrato, raia, tarefa, funil, negocio, atividade);
    }
    private record Fixture(Usuario usuario, Contrato contrato, ContratoKanbanRaia raia, ContratoKanbanTask tarefa,
                           PipelineVendasFunil funil, PipelineVendasNegocio negocio, PipelineVendasTarefa atividade) {}
}
