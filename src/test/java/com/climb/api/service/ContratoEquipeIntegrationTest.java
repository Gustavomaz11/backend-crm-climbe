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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false", "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=WARN", "logging.level.org.springframework=WARN",
        "app.pipeline.cadencia.atraso-inicial-ms=86400000",
        "app.pipeline.task-reminders.weekly-cron=-", "app.pipeline.task-reminders.overdue-cron=-", "app.kanban.prazos.cron=-"
})
@ActiveProfiles("test")
@Transactional
class ContratoEquipeIntegrationTest {
    @Autowired ContratoEquipeService equipe;
    @Autowired ContratoKanbanService kanban;
    @Autowired ContratoRateioTecnicoService rateio;
    @Autowired TarefaColaboracaoService colaboracao;
    @Autowired ContratoRepository contratos;
    @Autowired PropostaRepository propostas;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired ContratoKanbanTaskRepository tarefas;
    @Autowired ContratoRateioTecnicoRepository participantes;
    @Autowired ContratoApoioAtuacaoRepository atuacoes;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired KanbanPrazoAlertaService alertas;
    @Autowired KanbanPrazoNotificacaoRepository avisosPrazo;
    @Autowired PipelineVendasTarefaRepository tarefasComerciais;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean RbacService rbac;
    @MockitoBean EmailService email;
    @MockitoBean CloudflareR2ArquivoStorageService storage;
    @MockitoBean ZapSignClient assinatura;

    @Test
    void equipeColaboraSemPermissaoGlobalEApoioDuraAteUltimaConclusao() {
        var lider = usuario("Líder", "lider");
        var fixo = usuario("Equipe fixa", "fixo");
        var apoio = usuario("Colaborador de apoio", "apoio");
        var comercial = usuario("Comercial", "comercial");
        var contrato = contrato(lider, comercial);
        Long id = contrato.getIdContrato();
        assertThat(equipe.listarDisponiveis(lider.getId())).extracting(Contrato::getIdContrato).contains(id);
        assertThat(equipe.listarDisponiveis(comercial.getId())).isEmpty();
        var erro = assertThrows(ResponseStatusException.class, () -> kanban.buscarBoard(id, lider.getId()));
        assertThat(erro.getStatusCode().value()).isEqualTo(409);

        equipe.salvarEquipe(id, lider.getId(), List.of(fixo.getId(), fixo.getId()));
        equipe.salvarEquipe(id, lider.getId(), List.of(fixo.getId()));
        assertThat(notificacoes.findByUsuario_Id(fixo.getId())).hasSize(1);
        assertThat(kanban.buscarBoard(id, fixo.getId()).podeEditar()).isTrue();
        var board = kanban.criarRaia(id, fixo.getId(), new ContratoKanbanRaiaRequestDTO("A fazer", 0));
        Long pendente = board.raias().getFirst().id();
        board = kanban.criarRaia(id, fixo.getId(), new ContratoKanbanRaiaRequestDTO("Concluído", 1));
        Long concluido = board.raias().stream().filter(ContratoKanbanRaiaResponseDTO::concluiTarefas).findFirst().orElseThrow().id();
        kanban.criarTask(id, lider.getId(), tarefa(pendente, "Primeira tarefa", apoio.getId()));
        kanban.criarTask(id, lider.getId(), tarefa(pendente, "Segunda tarefa", apoio.getId()));
        var tarefasDoApoio = tarefas.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(id);
        Long primeira = tarefasDoApoio.get(0).getIdTask(), segunda = tarefasDoApoio.get(1).getIdTask();
        assertThat(notificacoes.findByUsuario_Id(apoio.getId())).hasSize(1);
        assertThat(equipe.buscarEquipe(id, lider.getId()).temporarios().getFirst().tarefas()).hasSize(2);
        assertThat(equipe.listarDisponiveis(apoio.getId())).extracting(Contrato::getIdContrato).contains(id);
        assertThat(kanban.buscarBoard(id, apoio.getId()).podeEditar()).isTrue();
        kanban.criarTask(id, apoio.getId(), tarefa(pendente, "Tarefa criada pelo apoio", apoio.getId()));
        colaboracao.comentar(TarefaTipo.CONTRATO, primeira, apoio.getId(), "Trabalho iniciado", null, List.of());
        assertThat(colaboracao.listar(TarefaTipo.CONTRATO, primeira, fixo.getId()).comentarios()).hasSize(1);

        kanban.moverTask(id, primeira, apoio.getId(), new ContratoKanbanMoverTaskRequestDTO(concluido));
        assertThat(kanban.buscarBoard(id, apoio.getId()).podeEditar()).isTrue();
        kanban.moverTask(id, segunda, apoio.getId(), new ContratoKanbanMoverTaskRequestDTO(concluido));
        // A pessoa pode criar tarefas, mas apenas o líder concede novo acesso de apoio.
        assertThat(equipe.listarDisponiveis(apoio.getId())).isEmpty();
        assertThat(assertThrows(ResponseStatusException.class, () -> kanban.buscarBoard(id, apoio.getId()))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(assertThrows(ResponseStatusException.class, () -> colaboracao.listar(TarefaTipo.CONTRATO, primeira, apoio.getId()))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(kanban.buscarBoard(id, fixo.getId()).podeEditar()).isTrue();

        YearMonth mes = YearMonth.from(tarefas.findById(primeira).orElseThrow().getConcluidaEm());
        var divisao = rateio.consultar(id, mes.toString(), lider.getId());
        assertThat(divisao.comissaoTecnicaTotal()).isEqualByComparingTo("100.01");
        assertThat(divisao.participantes()).hasSize(3);
        assertThat(divisao.participantes().stream().map(ContratoRateioTecnicoResponseDTO.Participante::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.01");
        assertThat(participantes.findByContrato_IdContratoAndCompetencia(id, mes.atDay(1))).hasSize(3);
        assertThat(rateio.consultar(id, mes.plusMonths(1).toString(), lider.getId()).participantes()).hasSize(2);
        kanban.criarTask(id, lider.getId(), tarefa(pendente, "Novo apoio após conclusão", apoio.getId()));
        assertThat(notificacoes.findByUsuario_Id(apoio.getId())).hasSize(2);
        assertThat(kanban.buscarBoard(id, apoio.getId()).podeEditar()).isTrue();
        verifyNoInteractions(email, storage, assinatura);
    }

    @Test
    void periodoDeApoioIncluiMesAnteriorEPreservaRateioAoRemoverTarefa() {
        var lider = usuario("Líder", "lider"); var fixo = usuario("Equipe fixa", "fixo");
        var apoio = usuario("Colaborador de apoio", "apoio"); var comercial = usuario("Comercial", "comercial");
        var contrato = contrato(lider, comercial); Long id = contrato.getIdContrato();
        YearMonth atual = YearMonth.now(ZoneId.of("America/Sao_Paulo")); YearMonth anterior = atual.minusMonths(1);
        var parcelaAnterior = new ContratoParcela(); parcelaAnterior.setContrato(contrato); parcelaAnterior.setNumero(1);
        parcelaAnterior.setCompetencia(anterior.atDay(1)); parcelaAnterior.setVencimento(anterior.atDay(1));
        parcelaAnterior.setValor(new BigDecimal("100.01"));
        contrato.getParcelas().forEach(p -> p.setNumero(p.getNumero() + 1)); contrato.getParcelas().add(parcelaAnterior);
        contrato.getProposta().setValuation(new BigDecimal("300.03")); contratos.saveAndFlush(contrato);
        equipe.salvarEquipe(id, lider.getId(), List.of(fixo.getId()));
        var board = kanban.criarRaia(id, lider.getId(), new ContratoKanbanRaiaRequestDTO("A fazer", 0));
        Long pendente = board.raias().getFirst().id();
        board = kanban.criarRaia(id, lider.getId(), new ContratoKanbanRaiaRequestDTO("Concluído", 1));
        Long concluido = board.raias().stream().filter(ContratoKanbanRaiaResponseDTO::concluiTarefas).findFirst().orElseThrow().id();
        kanban.criarTask(id, lider.getId(), tarefa(pendente, "Atuação desde o mês anterior", apoio.getId()));
        Long taskId = tarefas.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(id).getFirst().getIdTask();
        var periodo = atuacoes.findByTarefa_IdTask(taskId).getFirst();
        periodo.setInicio(anterior.atDay(15).atTime(9, 0)); atuacoes.saveAndFlush(periodo);
        // A consulta inclui períodos ativos, mesmo sem nenhuma movimentação naquele mês.
        assertThat(rateio.consultar(id, anterior.toString(), lider.getId()).participantes()).hasSize(3);
        assertThat(rateio.consultar(id, atual.toString(), lider.getId()).participantes()).hasSize(3);
        assertThat(rateio.consultar(id, atual.plusMonths(1).toString(), lider.getId()).participantes()).hasSize(2);
        kanban.moverTask(id, taskId, apoio.getId(), new ContratoKanbanMoverTaskRequestDTO(concluido));
        assertThat(equipe.listarDisponiveis(apoio.getId())).isEmpty();
        kanban.removerTask(id, taskId, lider.getId());
        var historico = atuacoes.findByContrato_IdContrato(id);
        assertThat(historico).hasSize(1); assertThat(historico.getFirst().getTarefa()).isNull();
        assertThat(historico.getFirst().getFim()).isNotNull();
        for (var mes : List.of(anterior, atual)) {
            var divisao = rateio.consultar(id, mes.toString(), lider.getId());
            assertThat(divisao.competenciaPagamento()).isEqualTo(mes.plusMonths(1).toString());
            assertThat(divisao.participantes()).hasSize(3);
            assertThat(divisao.participantes().stream().map(ContratoRateioTecnicoResponseDTO.Participante::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.01");
        }
        assertThat(rateio.consultar(id, atual.plusMonths(1).toString(), lider.getId()).participantes()).hasSize(2);
        verifyNoInteractions(email, storage, assinatura);
    }

    @Test
    void alertasAgrupadosNaoRepetemEConclusaoAtrasadaPreservaJustificativa() {
        var lider = usuario("Líder", "lider"); var apoio = usuario("Apoio", "apoio"); var comercial = usuario("Comercial", "comercial");
        var cargo = new Cargo(); cargo.setNome("  Diretor Comercial  "); em.persist(cargo);
        var diretor = usuario("Diretor", "diretor"); diretor.setCargo(cargo); usuarios.saveAndFlush(diretor);
        var outroDiretor = usuario("Outra diretora", "outra"); outroDiretor.setCargo(cargo); usuarios.saveAndFlush(outroDiretor);
        var inativo = usuario("Inativo", "inativo"); inativo.setCargo(cargo); inativo.setSituacao("INATIVO"); usuarios.saveAndFlush(inativo);
        assertThat(usuarios.buscarDiretoresComerciaisAtivos()).extracting(Usuario::getId).containsExactly(diretor.getId(), outroDiretor.getId());
        var contrato = contrato(lider, comercial); Long id = contrato.getIdContrato();
        equipe.salvarEquipe(id, lider.getId(), List.of(apoio.getId()));
        var board = kanban.criarRaia(id, lider.getId(), new ContratoKanbanRaiaRequestDTO("A fazer", 0)); Long pendente = board.raias().getFirst().id();
        board = kanban.criarRaia(id, lider.getId(), new ContratoKanbanRaiaRequestDTO("Concluído", 1));
        Long concluido = board.raias().stream().filter(ContratoKanbanRaiaResponseDTO::concluiTarefas).findFirst().orElseThrow().id();
        LocalDate hoje = TarefaPrazoPolicy.hoje(), vencido = hoje.minusDays(1);
        kanban.criarTask(id, lider.getId(), new ContratoKanbanTaskRequestDTO(pendente, "Entrega técnica", null,
                ContratoKanbanPrioridade.MEDIA, apoio.getId(), null, vencido, 0));
        Long tarefaId = tarefas.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(id).getFirst().getIdTask();
        var erro = assertThrows(ResponseStatusException.class, () -> kanban.atualizarTask(id, tarefaId, apoio.getId(),
                new ContratoKanbanTaskRequestDTO(pendente, "Entrega técnica", null, ContratoKanbanPrioridade.MEDIA, apoio.getId(), null, hoje, 0)));
        assertThat(erro.getStatusCode().value()).isEqualTo(403);
        assertThat(assertThrows(ResponseStatusException.class, () -> kanban.moverTask(id, tarefaId, apoio.getId(),
                new ContratoKanbanMoverTaskRequestDTO(concluido))).getStatusCode().value()).isEqualTo(400);
        // Repositórios reais: ignora atividades comerciais concluídas/canceladas e tarefas técnicas encerradas.
        var atividade = atividadeComercial(comercial, "Atividade comercial", hoje.plusDays(3), PipelineTarefaStatus.PENDENTE);
        atividadeComercial(comercial, "Já concluída", hoje, PipelineTarefaStatus.CONCLUIDA);
        atividadeComercial(comercial, "Cancelada", hoje, PipelineTarefaStatus.CANCELADA);
        em.flush();
        assertThat(tarefasComerciais.findAbertasComPrazoAte(hoje.plusDays(3))).extracting(PipelineVendasTarefa::getIdTarefa).containsExactly(atividade.getIdTarefa());
        org.mockito.Mockito.when(email.enviarEmailComConteudoHtml(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        alertas.processar(hoje); alertas.processar(hoje);
        assertThat(avisosPrazo.count()).isEqualTo(3);
        org.mockito.Mockito.verify(email, org.mockito.Mockito.times(1)).enviarEmailComConteudoHtml(org.mockito.ArgumentMatchers.eq(lider.getEmail()),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.argThat(html -> org.springframework.web.util.HtmlUtils.htmlUnescape(html).contains("Entrega técnica")), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(email, org.mockito.Mockito.times(1)).enviarEmailComConteudoHtml(org.mockito.ArgumentMatchers.eq(diretor.getEmail()),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.contains("Atividade comercial"), org.mockito.ArgumentMatchers.anyString());
        kanban.moverTask(id, tarefaId, apoio.getId(), new ContratoKanbanMoverTaskRequestDTO(concluido, "Documentos do cliente chegaram após o prazo."));
        assertThat(tarefas.buscarAbertasComPrazoAte(hoje.plusDays(3))).isEmpty();
        em.flush(); em.clear();
        assertThat(tarefas.findById(tarefaId).orElseThrow().getJustificativaAtraso()).isEqualTo("Documentos do cliente chegaram após o prazo.");
    }

    private PipelineVendasTarefa atividadeComercial(Usuario usuario, String titulo, LocalDate prazo, PipelineTarefaStatus status) {
        var funil = new PipelineVendasFunil(); funil.setCodigo(UUID.randomUUID().toString()); funil.setNome("Teste"); funil.setEstrategia("Todas"); funil.setPosicao(0); em.persist(funil);
        var etapa = new PipelineVendasEtapa(); etapa.setFunil(funil); etapa.setCodigo("DIAGNOSTICO"); etapa.setNome("Diagnóstico"); etapa.setPosicao(0); etapa.setResultado(PipelineVendasResultado.ABERTO); em.persist(etapa);
        var negocio = new PipelineVendasNegocio(); negocio.setFunil(funil); negocio.setEtapa(etapa); negocio.setResponsavel(usuario); negocio.setCriadoPor(usuario);
        negocio.setNomeEmpresa("Empresa comercial"); negocio.setNomeContato("Contato"); negocio.setOrigemNegocio("Manual"); negocio.setEstrategiaComercial("Todas"); negocio.setServicoInteresse("CONTABILIDADE");
        negocio.setCriadoEm(LocalDateTime.now()); negocio.setUltimaMovimentacaoEm(LocalDateTime.now()); em.persist(negocio);
        var tarefa = new PipelineVendasTarefa(); tarefa.setNegocio(negocio); tarefa.setCriadoPor(usuario); tarefa.setTitulo(titulo); tarefa.setResponsavel(usuario);
        tarefa.setPrioridade(PipelineTarefaPrioridade.MEDIA); tarefa.setStatus(status); tarefa.setTipo("Documentação"); tarefa.setPrazo(prazo); em.persist(tarefa); return tarefa;
    }

    private Usuario usuario(String nome, String chave) {
        Usuario u = new Usuario(); u.setNomeCompleto(nome); u.setEmail(chave + "@example.test");
        u.setContato("11999999999"); u.setSenhaHash("test"); u.setSituacao("ATIVO");
        return usuarios.saveAndFlush(u);
    }
    private Contrato contrato(Usuario lider, Usuario comercial) {
        Empresa e = new Empresa(); e.setRazaoSocial("Empresa de integração"); e.setNomeFantasia("Empresa de integração");
        e.setCnpj("11.222.333/0001-81"); empresas.saveAndFlush(e);
        Proposta p = new Proposta(); p.setEmpresa(e); p.setUsuario(comercial); p.setStatus(PropostaStatus.APROVADA);
        p.setServico(ServicoComercial.CONTABILIDADE); p.setValuation(new BigDecimal("200.02"));
        p.setComissaoTecnicoPercentual(new BigDecimal("100")); propostas.saveAndFlush(p);
        Contrato c = new Contrato(); c.setEmpresa(e); c.setEmpresaNomeFantasia(e.getNomeFantasia());
        c.setUsuario(comercial); c.setResponsavelComercial(comercial); c.setResponsavel(lider);
        c.setParticipantes(new HashSet<>(Set.of(comercial))); c.setProposta(p); c.setStatus(ContratoService.STATUS_APROVADO);
        LocalDate mes = LocalDate.now(ZoneId.of("America/Sao_Paulo")).withDayOfMonth(1);
        for (int i = 0; i < 2; i++) {
            ContratoParcela parcela = new ContratoParcela(); parcela.setContrato(c); parcela.setNumero(i + 1);
            parcela.setCompetencia(mes.plusMonths(i)); parcela.setVencimento(mes.plusMonths(i));
            parcela.setValor(new BigDecimal("100.01")); c.getParcelas().add(parcela);
        }
        return contratos.saveAndFlush(c);
    }
    private ContratoKanbanTaskRequestDTO tarefa(Long raiaId, String titulo, Long usuarioId) {
        return new ContratoKanbanTaskRequestDTO(raiaId, titulo, null, ContratoKanbanPrioridade.MEDIA, null,
                LocalDate.now().plusMonths(3), LocalDate.now().plusMonths(4), null, List.of(usuarioId));
    }
}
