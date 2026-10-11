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
    @Autowired ApplicationContext context;

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
