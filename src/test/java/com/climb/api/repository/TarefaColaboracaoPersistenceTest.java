package com.climb.api.repository;

import com.climb.api.model.*;
import com.climb.api.model.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false", "spring.mail.host=localhost",
    "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "logging.level.org.springframework=WARN",
    "app.pipeline.cadencia.atraso-inicial-ms=86400000",
    "app.pipeline.task-reminders.weekly-cron=-", "app.pipeline.task-reminders.overdue-cron=-"
})
@ActiveProfiles("test") @Transactional
class TarefaColaboracaoPersistenceTest {
    @Autowired EntityManager em;
    @Autowired ContratoKanbanTaskRepository contratos;
    @Autowired PipelineVendasTarefaRepository comercial;
    @Autowired TarefaComentarioRepository comentarios;
    @Autowired TarefaAnexoRepository anexos;
    @Autowired TarefaPastaRepository pastas;

    @Test void devePersistirResponsaveisSubtarefasEConversaEFiltrarPeloSegundoResponsavel() {
        Usuario ana = usuario("Ana"); Usuario bia = usuario("Bia");
        Empresa empresa = new Empresa(); empresa.setRazaoSocial("Empresa"); empresa.setNomeFantasia("Empresa"); empresa.setCnpj("11.222.333/0001-81"); em.persist(empresa);
        Contrato contrato = new Contrato(); contrato.setUsuario(ana); contrato.setEmpresa(empresa); contrato.setEmpresaNomeFantasia("Empresa"); contrato.setStatus("APROVADO"); contrato.setResponsavel(ana); em.persist(contrato);
        ContratoKanbanRaia raia = new ContratoKanbanRaia(); raia.setContrato(contrato); raia.setTitulo("A fazer"); raia.setPosicao(0); em.persist(raia);
        ContratoKanbanTask task = new ContratoKanbanTask(); task.setContrato(contrato); task.setRaia(raia); task.setTitulo("Análise"); task.setResponsaveis(List.of(ana, bia)); em.persist(task);
        ContratoKanbanSubtarefa sub = new ContratoKanbanSubtarefa(); sub.setTask(task); sub.setTitulo("Conferir"); sub.setResponsavel(bia); em.persist(sub);
        PipelineVendasFunil funil = new PipelineVendasFunil(); funil.setCodigo("TESTE"); funil.setNome("Teste"); funil.setEstrategia("Todas"); funil.setPosicao(0); em.persist(funil);
        PipelineVendasEtapa etapa = new PipelineVendasEtapa(); etapa.setFunil(funil); etapa.setCodigo("DIAGNOSTICO"); etapa.setNome("Diagnóstico"); etapa.setPosicao(0); etapa.setResultado(PipelineVendasResultado.ABERTO); em.persist(etapa);
        PipelineVendasNegocio negocio = new PipelineVendasNegocio(); negocio.setFunil(funil); negocio.setEtapa(etapa); negocio.setResponsavel(ana); negocio.setCriadoPor(ana);
        negocio.setNomeEmpresa("Empresa"); negocio.setNomeContato("Contato"); negocio.setOrigemNegocio("Manual"); negocio.setEstrategiaComercial("Todas"); negocio.setServicoInteresse("BPO");
        negocio.setCriadoEm(java.time.LocalDateTime.now()); negocio.setUltimaMovimentacaoEm(java.time.LocalDateTime.now()); em.persist(negocio);
        PipelineVendasTarefa atividade = new PipelineVendasTarefa(); atividade.setNegocio(negocio); atividade.setCriadoPor(ana); atividade.setTitulo("Ligação"); atividade.setResponsaveis(List.of(ana, bia));
        atividade.setPrioridade(PipelineTarefaPrioridade.MEDIA); atividade.setStatus(PipelineTarefaStatus.PENDENTE); atividade.setTipo("Ligação"); atividade.setPrazo(LocalDate.now());
        PipelineVendasSubtarefa checklist = new PipelineVendasSubtarefa(); checklist.setTitulo("Contato"); checklist.setPosicao(0); checklist.setResponsavel(bia); atividade.substituirSubtarefas(List.of(checklist)); em.persist(atividade);
        TarefaComentario comentario = new TarefaComentario(); comentario.setContratoTask(task); comentario.setAutor(ana); comentario.setConteudo("Documentos enviados"); em.persist(comentario);
        TarefaComentario resposta = new TarefaComentario(); resposta.setContratoTask(task); resposta.setAutor(bia); resposta.setConteudo("Conferidos"); resposta.setComentarioPai(comentario); em.persist(resposta);
        TarefaAnexo arquivo = new TarefaAnexo(); arquivo.setContratoTask(task); arquivo.setComentario(resposta); arquivo.setAutor(bia); arquivo.setNome("balanco.pdf"); arquivo.setContentType("application/pdf"); arquivo.setTamanho(10); arquivo.setChave("tarefas/chave"); em.persist(arquivo);
        var raiz = pasta(task, null, ana, "Documentos", null);
        var filha = pasta(task, null, bia, "2026", raiz);
        var comercialRaiz = pasta(null, atividade, ana, "Comercial", null);
        pasta(null, atividade, bia, "Propostas", comercialRaiz);
        TarefaAnexo organizado = new TarefaAnexo(); organizado.setContratoTask(task); organizado.setPasta(filha); organizado.setAutor(bia); organizado.setNome("outro.pdf"); organizado.setContentType("application/pdf"); organizado.setTamanho(10); organizado.setChave("tarefas/pastas/chave"); em.persist(organizado);
        em.flush(); em.clear();
        var tarefasContrato = contratos.findByContrato_IdContratoAndResponsavel_IdInOrderByRaia_PosicaoAscPosicaoAscIdTaskAsc(contrato.getIdContrato(), Set.of(bia.getId()));
        assertEquals(1, tarefasContrato.size()); assertEquals(2, tarefasContrato.getFirst().getResponsaveisEfetivos().size());
        assertEquals(Set.of(bia.getId()), contratos.findResponsavelIdsComTasks(contrato.getIdContrato(), Set.of(bia.getId())));
        var tarefasComerciais = comercial.findFiltradas(true, false, false, false, false, LocalDate.now(), false, Set.of(bia.getId()), negocio.getIdNegocio(), null, null);
        assertEquals(1, tarefasComerciais.size()); assertEquals(2, tarefasComerciais.getFirst().getResponsaveisEfetivos().size());
        assertEquals(bia.getId(), tarefasComerciais.getFirst().getSubtarefas().getFirst().getResponsavel().getId());
        assertEquals(bia.getId(), em.find(ContratoKanbanSubtarefa.class, sub.getIdSubtarefa()).getResponsavel().getId());
        var conversa = comentarios.findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(task.getIdTask());
        assertEquals(2, conversa.size()); assertEquals(comentario.getId(), conversa.get(1).getComentarioPai().getId());
        var arquivos = anexos.findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(task.getIdTask());
        assertEquals(resposta.getId(), arquivos.getFirst().getComentario().getId());
        assertEquals(filha.getId(), arquivos.stream().filter(item -> item.getId().equals(organizado.getId())).findFirst().orElseThrow().getPasta().getId());
        var diretorios = pastas.findByContratoTask_IdTaskOrderByNomeAscIdAsc(task.getIdTask());
        assertEquals(2, diretorios.size());
        assertEquals(raiz.getId(), diretorios.stream().filter(item -> item.getId().equals(filha.getId())).findFirst().orElseThrow().getPastaPai().getId());
        assertEquals(2, pastas.findByPipelineTarefa_IdTarefaOrderByNomeAscIdAsc(atividade.getIdTarefa()).size());
    }
    private TarefaPasta pasta(ContratoKanbanTask contrato, PipelineVendasTarefa pipeline, Usuario autor, String nome, TarefaPasta pai) {
        var pasta = new TarefaPasta(); pasta.setContratoTask(contrato); pasta.setPipelineTarefa(pipeline); pasta.setAutor(autor); pasta.setNome(nome); pasta.setPastaPai(pai); em.persist(pasta); return pasta;
    }
    private Usuario usuario(String nome) {
        Usuario usuario = new Usuario(); usuario.setNomeCompleto(nome); usuario.setEmail(nome + "@example.test"); usuario.setContato("11999999999"); usuario.setSenhaHash("test"); usuario.setSituacao("ATIVO"); em.persist(usuario); return usuario;
    }
}
