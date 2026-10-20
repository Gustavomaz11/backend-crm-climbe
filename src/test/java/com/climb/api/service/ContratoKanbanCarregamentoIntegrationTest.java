package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.*;
import com.climb.api.model.dto.ContratoKanbanResumoDTO;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "debug=false",
        "logging.level.root=WARN", "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.pipeline.cadencia.atraso-inicial-ms=86400000", "app.notificacoes.email.enabled=false"
})
@ActiveProfiles("test")
@Transactional
class ContratoKanbanCarregamentoIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ContratoEquipeService equipes;
    @Autowired ContratoKanbanService kanban;
    @Autowired ContratoService contratosService;
    @Autowired ApplicationContext context;
    @Autowired ContratoServicoEncerramentoService encerramentos;
    @Autowired ContratoKanbanTaskRepository tarefas;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired ContratoApoioAtuacaoRepository atuacoes;

    @Test
    void encerraApenasServicoSelecionadoMantemHistoricoEInterrompeApoioEAlertas() {
        Usuario contabil = usuario("contabil_encerrar"), cfo = usuario("cfo_encerrar"), apoio = usuario("apoio_encerrar");
        Contrato contrato = contratoComDoisServicos(contabil, cfo);
        Long id = contrato.getIdContrato(); equipes.salvarEquipe(id, cfo.getId(), List.of(contabil.getId()));
        var quadro = kanban.criarRaia(id, cfo.getId(), new com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO(
                "A fazer", 0, false, ServicoComercial.CFO));
        Long raiaId = quadro.raias().getFirst().id();
        kanban.criarTask(id, cfo.getId(), pedido(raiaId, "Planejamento CFO", List.of(apoio.getId())));
        var tarefa = tarefas.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(id).getFirst();
        Long tarefaId = tarefa.getIdTask();
        assertThat(equipes.temDisponiveis(apoio.getId())).isTrue();
        assertThat(tarefas.buscarAbertasComPrazoAte(TarefaPrazoPolicy.hoje().plusDays(3))).contains(tarefa);
        assertThat(atuacoes.findByTarefa_IdTaskAndFimIsNull(tarefaId)).hasSize(1);

        var resultado = encerramentos.encerrar(id, ServicoComercial.CFO, cfo.getId()); em.flush(); em.clear();
        contrato = em.find(Contrato.class, id);
        assertThat(contrato.getStatus()).isEqualTo("APROVADO");
        assertThat(contrato.getEncerramentosPorServico()).containsOnlyKeys("CFO");
        assertThat(contrato.getEncerramentosPorServico().get("CFO").getEncerradoPor().getId()).isEqualTo(cfo.getId());
        assertThat(em.find(ContratoKanbanTask.class, tarefaId).getTitulo()).isEqualTo("Planejamento CFO");
        assertThat(em.find(ContratoKanbanTask.class, tarefaId).getConcluidaEm()).isNull();
        assertThat(atuacoes.findByTarefa_IdTaskAndFimIsNull(tarefaId)).isEmpty();
        assertThat(tarefas.buscarAbertasComPrazoAte(TarefaPrazoPolicy.hoje().plusDays(3))).isEmpty();
        assertThat(tarefas.buscarApoiosPendentes(id)).isEmpty();
        assertThat(equipes.temDisponiveis(apoio.getId())).isFalse();
        assertThat(equipes.listarResumos(cfo.getId()).getFirst().servicos())
                .extracting(ContratoKanbanResumoDTO.ServicoResumo::servico).containsExactly(ServicoComercial.CONTABILIDADE);
        assertThat(kanban.buscarBoard(id, contabil.getId()).servico()).isEqualTo(ServicoComercial.CONTABILIDADE);
        assertThat(kanban.buscarBoard(id, contabil.getId(), ServicoComercial.CONTABILIDADE).raias()).isEmpty();
        assertThatThrownBy(() -> kanban.buscarBoard(id, cfo.getId(), ServicoComercial.CFO)).hasMessageContaining("encerrado");
        assertThatThrownBy(() -> kanban.atualizarTask(id, tarefaId, cfo.getId(), pedido(raiaId, "Alterada", List.of(cfo.getId()))))
                .hasMessageContaining("encerrado");
        assertThatThrownBy(() -> kanban.exigirTaskVisivel(tarefaId, cfo.getId())).hasMessageContaining("encerrado");
        assertThat(encerramentos.encerrar(id, ServicoComercial.CFO, cfo.getId()).encerradoEm()).isEqualTo(resultado.encerradoEm());
        encerramentos.encerrar(id, ServicoComercial.CONTABILIDADE, contabil.getId()); em.flush();
        assertThat(equipes.temDisponiveis(cfo.getId())).isFalse();
        assertThat(equipes.listarResumos(contabil.getId())).isEmpty();
        assertThat(equipes.listarDisponiveis(cfo.getId())).isEmpty();
    }

    @Test
    void apenasResponsavelDoServicoPodeEncerrarInclusiveAntesDeConfigurarEquipe() {
        Usuario contabil = usuario("responsavel_contabil"), cfo = usuario("responsavel_cfo"), leitor = usuario("admin_encerrar");
        concederLeituraGlobal(leitor);
        Contrato contrato = contratoComDoisServicos(contabil, cfo); contrato.setEquipeConfigurada(false);
        Long id = contrato.getIdContrato();
        assertThat(kanban.buscarAbertura(id, cfo.getId(), ServicoComercial.CFO).podeEncerrar()).isTrue();
        assertThat(kanban.buscarAbertura(id, contabil.getId(), ServicoComercial.CFO).podeEncerrar()).isFalse();
        for (Usuario usuario : List.of(contabil, leitor)) {
            assertThatThrownBy(() -> encerramentos.encerrar(id, ServicoComercial.CFO, usuario.getId()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, erro -> assertThat(erro.getStatusCode().value()).isEqualTo(403));
        }
        assertThatThrownBy(() -> encerramentos.encerrar(id, null, cfo.getId())).hasMessageContaining("Selecione o serviço");
        assertThatThrownBy(() -> encerramentos.encerrar(id, ServicoComercial.BPO, cfo.getId())).hasMessageContaining("não pertence");
        assertThat(contrato.getEncerramentosPorServico()).isEmpty();
        encerramentos.encerrar(id, ServicoComercial.CFO, cfo.getId()); em.flush();
        assertThat(equipes.listarResumos(contabil.getId()).getFirst().servicos()).hasSize(1);
    }

    @Test
    void encerraContratoLegadoSemServicoSemExcluirDados() {
        Usuario lider = usuario("legado_encerrar"); Contrato contrato = contrato(lider, empresa(), true);
        Long id = contrato.getIdContrato();
        encerramentos.encerrar(id, null, lider.getId()); em.flush(); em.clear();
        assertThat(em.find(Contrato.class, id).getEncerramentosPorServico()).containsOnlyKeys("SEM_SERVICO");
        assertThat(equipes.listarResumos(lider.getId())).isEmpty();
        assertThat(equipes.temDisponiveis(lider.getId())).isFalse();
    }

    @Test
    void notificaCadaNovoResponsavelSemRepetirAoEditarEIncluiServicoNoDestino() {
        Usuario contabil = usuario("contabil_avisos"), cfo = usuario("cfo_avisos"), ana = usuario("ana_avisos"), bia = usuario("bia_avisos");
        Contrato contrato = contratoComDoisServicos(contabil, cfo); Long id = contrato.getIdContrato();
        equipes.salvarEquipe(id, cfo.getId(), List.of(ana.getId(), bia.getId()));
        var quadro = kanban.criarRaia(id, cfo.getId(), new com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO("A fazer", 0, false, ServicoComercial.CFO));
        Long raiaId = quadro.raias().getFirst().id();
        kanban.criarTask(id, cfo.getId(), pedido(raiaId, "Nova análise", List.of(ana.getId())));
        Long tarefaId = tarefas.findByContrato_IdContratoOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(id).getFirst().getIdTask();
        kanban.atualizarTask(id, tarefaId, cfo.getId(), pedido(raiaId, "Nova análise", List.of(ana.getId(), bia.getId())));
        kanban.atualizarTask(id, tarefaId, cfo.getId(), pedido(raiaId, "Título atualizado", List.of(ana.getId(), bia.getId())));
        kanban.atualizarTask(id, tarefaId, cfo.getId(), pedido(raiaId, "Título atualizado", List.of(bia.getId())));
        for (Usuario destinatario : List.of(ana, bia)) {
            assertThat(notificacoes.findByUsuario_Id(destinatario.getId()).stream().filter(n -> "TAREFA_ATRIBUIDA".equals(n.getTipo())))
                    .hasSize(1).allSatisfy(n -> {
                        assertThat(n.getLida()).isFalse();
                        assertThat(n.getDestino()).contains("contrato=" + id, "tarefaId=" + tarefaId, "servico=CFO");
                    });
        }
        assertThat(notificacoes.findByUsuario_Id(contabil.getId()).stream().filter(n -> "TAREFA_ATRIBUIDA".equals(n.getTipo()))).isEmpty();
    }

    private com.climb.api.model.dto.ContratoKanbanTaskRequestDTO pedido(Long raia, String titulo, List<Long> responsaveis) {
        return new com.climb.api.model.dto.ContratoKanbanTaskRequestDTO(raia, titulo, null,
                com.climb.api.model.enums.ContratoKanbanPrioridade.MEDIA, null, null, TarefaPrazoPolicy.hoje().plusDays(2), 0, responsaveis);
    }

    private Contrato contratoComDoisServicos(Usuario contabil, Usuario cfo) {
        Contrato contrato = contrato(contabil, empresa(), true);
        Proposta proposta = new Proposta(); proposta.setEmpresa(contrato.getEmpresa()); proposta.setUsuario(contabil);
        proposta.setStatus(PropostaStatus.APROVADA); proposta.setServicos(List.of(
                new PropostaServico(ServicoComercial.CONTABILIDADE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)));
        em.persist(proposta); contrato.setProposta(proposta);
        contrato.getResponsaveisPorServico().put(ServicoComercial.CONTABILIDADE, contabil);
        contrato.getResponsaveisPorServico().put(ServicoComercial.CFO, cfo);
        return contrato;
    }

    @Test
    void persisteResponsaveisPorServicoELiberaCadaLiderNoSeuQuadro() {
        Usuario contabil = usuario("lider_contabil"); Usuario cfo = usuario("lider_cfo"); Empresa empresa = empresa();
        Proposta proposta = new Proposta(); proposta.setEmpresa(empresa); proposta.setUsuario(contabil);
        proposta.setStatus(PropostaStatus.APROVADA);
        proposta.setServicos(List.of(
                new PropostaServico(ServicoComercial.CONTABILIDADE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)));
        em.persist(proposta); em.flush();
        Contrato contrato = contratosService.criarPreparacaoDoPipeline(proposta, contabil, contabil, contabil,
                java.util.Map.of(ServicoComercial.CONTABILIDADE, contabil, ServicoComercial.CFO, cfo));
        contrato.setStatus("APROVADO");
        Long contratoId = contrato.getIdContrato(); Long cfoId = cfo.getId(); Long contabilId = contabil.getId();
        em.flush(); em.clear();

        contrato = em.find(Contrato.class, contratoId);
        assertThat(contrato.getResponsaveisPorServico()).hasSize(2);
        assertThat(contrato.getParticipantes()).extracting(Usuario::getId).contains(contabilId, cfoId);
        assertThat(equipes.listarResumos(cfoId)).extracting(ContratoKanbanResumoDTO::id).containsExactly(contratoId);
        assertThat(equipes.temDisponiveis(cfoId)).isTrue();
        assertThat(equipes.buscarEquipe(contratoId, cfoId).lider()).isTrue();
        var equipe = equipes.salvarEquipe(contratoId, cfoId, List.of(cfoId));
        assertThat(equipe.membros()).extracting(u -> u.id()).contains(contabilId, cfoId);

        var quadro = kanban.buscarBoard(contratoId, cfoId, ServicoComercial.CFO);
        assertThat(quadro.gestor()).isTrue();
        assertThat(quadro.responsavel().id()).isEqualTo(cfoId);
        var outro = kanban.buscarBoard(contratoId, cfoId, ServicoComercial.CONTABILIDADE);
        assertThat(outro.gestor()).isFalse();
        assertThat(outro.responsavel().id()).isEqualTo(contabilId);

        var raia = new ContratoKanbanRaia(); raia.setContrato(contrato); raia.setServico(ServicoComercial.CFO);
        raia.setTitulo("A fazer CFO"); em.persist(raia);
        var tarefa = new ContratoKanbanTask(); tarefa.setContrato(contrato); tarefa.setRaia(raia); tarefa.setTitulo("Planejamento");
        tarefa.setDataFim(TarefaPrazoPolicy.hoje().plusDays(2)); em.persist(tarefa); em.flush();
        var alteracao = new com.climb.api.model.dto.ContratoKanbanTaskRequestDTO(raia.getIdRaia(), "Planejamento", null,
                com.climb.api.model.enums.ContratoKanbanPrioridade.MEDIA, cfoId, null, TarefaPrazoPolicy.hoje().plusDays(4), 0);
        assertThatThrownBy(() -> kanban.atualizarTask(contratoId, tarefa.getIdTask(), contabilId, alteracao))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("responsável técnico do serviço");
        kanban.atualizarTask(contratoId, tarefa.getIdTask(), cfoId, alteracao);
        assertThat(tarefa.getDataFim()).isEqualTo(alteracao.dataFim());
    }

    @Test
    void mesmoResponsavelPodeSerPersistidoEmMaisDeUmServico() {
        Usuario lider = usuario("lider_dois_servicos");
        Contrato contrato = contrato(lider, empresa(), true);
        contrato.getResponsaveisPorServico().put(ServicoComercial.CONTABILIDADE, lider);
        contrato.getResponsaveisPorServico().put(ServicoComercial.CFO, lider);
        Long contratoId = contrato.getIdContrato(); Long liderId = lider.getId(); em.flush(); em.clear();
        assertThat(em.find(Contrato.class, contratoId).getResponsaveisPorServico().values())
                .extracting(Usuario::getId).containsExactlyInAnyOrder(liderId, liderId);
    }

    @Test
    void servicosDoMesmoContratoPossuemColunasETarefasIndependentes() {
        Usuario lider = usuario("lider_servicos"); Empresa empresa = empresa();
        Contrato contrato = contrato(lider, empresa, true);
        Proposta proposta = new Proposta(); proposta.setEmpresa(empresa); proposta.setUsuario(lider);
        proposta.setStatus(PropostaStatus.APROVADA);
        proposta.setServicos(List.of(
                new PropostaServico(ServicoComercial.CONTABILIDADE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)));
        em.persist(proposta); contrato.setProposta(proposta);
        var contabil = new ContratoKanbanRaia(); contabil.setContrato(contrato); contabil.setTitulo("Contabilidade antiga");
        em.persist(contabil); // Coluna anterior à separação: pertence ao primeiro serviço.
        var cfo = new ContratoKanbanRaia(); cfo.setContrato(contrato); cfo.setServico(ServicoComercial.CFO); cfo.setTitulo("CFO");
        cfo.setConcluiTarefas(true); em.persist(cfo);
        var taskContabil = new ContratoKanbanTask(); taskContabil.setContrato(contrato); taskContabil.setRaia(contabil);
        taskContabil.setTitulo("Fechamento contábil"); em.persist(taskContabil);
        var taskCfo = new ContratoKanbanTask(); taskCfo.setContrato(contrato); taskCfo.setRaia(cfo);
        taskCfo.setTitulo("Análise financeira"); em.persist(taskCfo); em.flush();

        var quadroContabil = kanban.buscarBoard(contrato.getIdContrato(), lider.getId(), ServicoComercial.CONTABILIDADE);
        var quadroCfo = kanban.buscarAbertura(contrato.getIdContrato(), lider.getId(), ServicoComercial.CFO).board();
        assertThat(quadroContabil.servico()).isEqualTo(ServicoComercial.CONTABILIDADE);
        assertThat(quadroContabil.raias()).extracting(r -> r.titulo()).containsExactly("Contabilidade antiga");
        assertThat(quadroContabil.raias().getFirst().tasks()).extracting(t -> t.titulo()).containsExactly("Fechamento contábil");
        assertThat(quadroCfo.servico()).isEqualTo(ServicoComercial.CFO);
        assertThat(quadroCfo.raias()).extracting(r -> r.titulo()).containsExactly("CFO");
        assertThat(quadroCfo.raias().getFirst().tasks()).extracting(t -> t.titulo()).containsExactly("Análise financeira");
        assertThat(kanban.buscarBoard(contrato.getIdContrato(), lider.getId()).servico()).isEqualTo(ServicoComercial.CONTABILIDADE);
        assertThatThrownBy(() -> kanban.moverTask(contrato.getIdContrato(), taskContabil.getIdTask(), lider.getId(),
                new com.climb.api.model.dto.ContratoKanbanMoverTaskRequestDTO(cfo.getIdRaia())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("seu serviço");
        assertThatThrownBy(() -> kanban.buscarBoard(contrato.getIdContrato(), lider.getId(), ServicoComercial.BPO))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("não pertence");

        kanban.alterarArquivamento(contrato.getIdContrato(), taskCfo.getIdTask(), lider.getId(), true);
        assertThat(kanban.listarArquivados(contrato.getIdContrato(), lider.getId(), ServicoComercial.CONTABILIDADE)).isEmpty();
        assertThat(kanban.listarArquivados(contrato.getIdContrato(), lider.getId(), ServicoComercial.CFO))
                .extracting(item -> item.id()).containsExactly(taskCfo.getIdTask());
        var criado = kanban.criarRaia(contrato.getIdContrato(), lider.getId(),
                new com.climb.api.model.dto.ContratoKanbanRaiaRequestDTO("Nova CFO", null, false, ServicoComercial.CFO));
        assertThat(criado.servico()).isEqualTo(ServicoComercial.CFO);
        assertThat(criado.raias()).extracting(r -> r.titulo()).containsExactly("CFO", "Nova CFO");
    }

    @Test
    void listagemNaoMultiplicaConsultasDeAcessoPelaQuantidadeDeContratos() {
        Usuario lider = usuario("lider");
        Usuario leitor = usuario("leitor");
        concederLeituraGlobal(leitor);
        Empresa empresa = empresa();
        for (int i = 0; i < 8; i++) contrato(lider, empresa, true);
        em.flush(); em.clear();
        var estatisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        assertThat(equipes.listarDisponiveis(leitor.getId())).hasSize(8);
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        System.out.println("Consultas listagem legada de 8 contratos: " + estatisticas.getPrepareStatementCount());
    }

    @Test
    void resumoCarregaMultiplosServicosOrdenadosSemConsultarGrafosCompletos() {
        Usuario lider = usuario("lider"); Usuario leitor = usuario("leitor");
        concederLeituraGlobal(leitor);
        Empresa empresa = empresa();
        for (int i = 0; i < 8; i++) {
            Contrato contrato = contrato(lider, empresa, true);
            Proposta proposta = new Proposta(); proposta.setEmpresa(empresa); proposta.setUsuario(lider);
            proposta.setStatus(PropostaStatus.APROVADA);
            proposta.setServicos(List.of(
                    new PropostaServico(ServicoComercial.VALUATION, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                    new PropostaServico(ServicoComercial.DESENVOLVIMENTO_SOFTWARE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN)));
            em.persist(proposta); contrato.setProposta(proposta);
        }
        Contrato legado = contrato(lider, empresa, true); legado.setServico(ServicoComercial.CONTABILIDADE);
        em.flush(); em.clear();
        var estatisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        var resumos = equipes.listarResumos(leitor.getId());
        assertThat(resumos).hasSize(9);
        assertThat(resumos.subList(0, 8)).allSatisfy(resumo -> assertThat(resumo.servicos())
                .extracting(ContratoKanbanResumoDTO.ServicoResumo::servico)
                .containsExactly(ServicoComercial.VALUATION, ServicoComercial.DESENVOLVIMENTO_SOFTWARE));
        assertThat(resumos.getLast().servico()).isEqualTo(ServicoComercial.CONTABILIDADE);
        assertThat(resumos.getLast().servicos()).isEmpty();
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(2);
        assertThat(estatisticas.getEntityLoadCount()).isZero();
        System.out.println("Consultas resumos de 9 contratos: " + estatisticas.getPrepareStatementCount());
    }

    @Test
    void filtroSqlPreservaAcessoDoLiderEquipeEApoiosSomenteEnquantoAtribuidosEPendentes() {
        Usuario lider = usuario("lider"); Usuario fixo = usuario("fixo");
        Usuario apoio = usuario("apoio"); Usuario externo = usuario("externo"); Usuario leitor = usuario("leitor");
        concederLeituraGlobal(leitor);
        Empresa empresa = empresa();
        Contrato aberto = contrato(lider, empresa, true);
        Contrato semEquipe = contrato(lider, empresa, false);
        Contrato pendente = contrato(lider, empresa, true); pendente.setStatus("PENDENTE");
        membro(aberto, fixo); membro(semEquipe, fixo);
        ContratoKanbanRaia raia = new ContratoKanbanRaia(); raia.setContrato(aberto); raia.setTitulo("A fazer"); em.persist(raia);
        ContratoKanbanTask tarefa = new ContratoKanbanTask(); tarefa.setContrato(aberto); tarefa.setRaia(raia);
        tarefa.setTitulo("Atividade"); tarefa.setResponsaveis(List.of(apoio)); tarefa.getApoios().add(apoio); em.persist(tarefa);
        em.flush();

        assertThat(equipes.listarResumos(lider.getId())).extracting(ContratoKanbanResumoDTO::id)
                .containsExactly(aberto.getIdContrato(), semEquipe.getIdContrato());
        assertThat(equipes.listarResumos(fixo.getId())).extracting(ContratoKanbanResumoDTO::id).containsExactly(aberto.getIdContrato());
        assertThat(equipes.listarResumos(apoio.getId())).extracting(ContratoKanbanResumoDTO::id).containsExactly(aberto.getIdContrato());
        assertThat(equipes.temDisponiveis(apoio.getId())).isTrue();
        assertThat(equipes.temDisponiveis(externo.getId())).isFalse();
        assertThat(equipes.listarResumos(leitor.getId())).hasSize(2);
        assertThatThrownBy(() -> kanban.buscarAbertura(aberto.getIdContrato(), externo.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> assertThat(erro.getStatusCode().value()).isEqualTo(403));
        assertThat(kanban.buscarAbertura(aberto.getIdContrato(), fixo.getId()).board().podeEditar()).isTrue();
        assertThat(kanban.buscarAbertura(aberto.getIdContrato(), leitor.getId()).board().podeEditar()).isFalse();
        var abertura = kanban.buscarAbertura(semEquipe.getIdContrato(), lider.getId());
        assertThat(abertura.equipe().configurada()).isFalse(); assertThat(abertura.board()).isNull();

        raia.setConcluiTarefas(true); em.flush();
        assertThat(equipes.temDisponiveis(apoio.getId())).isFalse();
        raia.setConcluiTarefas(false); tarefa.setResponsaveis(List.of(lider)); em.flush();
        assertThat(equipes.temDisponiveis(apoio.getId())).isFalse();
    }

    @Test
    void envioDeEmailsPossuiAgendadorSeparadoDasDemaisRotinas() {
        assertThat(context.getBean("taskScheduler")).isNotSameAs(context.getBean("notificationEmailScheduler"));
    }

    private void concederLeituraGlobal(Usuario usuario) {
        Permissao permissao = new Permissao();
        permissao.setCodigo(PermissaoCodigo.CONTRATO_KANBAN_VISUALIZAR_TODAS_TAREFAS.name());
        em.persist(permissao); usuario.getPermissoes().add(permissao);
    }

    private void membro(Contrato contrato, Usuario usuario) {
        ContratoEquipeMembro membro = new ContratoEquipeMembro(); membro.setContrato(contrato); membro.setUsuario(usuario);
        em.persist(membro);
    }

    private Usuario usuario(String nome) {
        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(nome); usuario.setEmail(nome + "@carregamento.test");
        usuario.setContato("71999999999"); usuario.setSituacao("ATIVO"); usuario.setSenhaHash("test");
        em.persist(usuario); return usuario;
    }

    private Empresa empresa() {
        Empresa empresa = new Empresa(); empresa.setRazaoSocial("Empresa"); empresa.setNomeFantasia("Empresa");
        empresa.setCnpj("11.222.333/0001-81"); em.persist(empresa); return empresa;
    }

    private Contrato contrato(Usuario lider, Empresa empresa, boolean configurada) {
        Contrato contrato = new Contrato(); contrato.setUsuario(lider); contrato.setResponsavel(lider);
        contrato.setEmpresa(empresa); contrato.setEmpresaNomeFantasia(empresa.getNomeFantasia());
        contrato.setStatus("APROVADO"); contrato.setEquipeConfigurada(configurada);
        em.persist(contrato); return contrato;
    }
}
