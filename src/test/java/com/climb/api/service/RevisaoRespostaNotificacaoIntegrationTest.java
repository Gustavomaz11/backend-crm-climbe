package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.*;
import com.climb.api.model.enums.*;
import com.climb.api.repository.NotificacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "logging.level.root=WARN", "debug=false",
        "app.pipeline.cadencia.atraso-inicial-ms=86400000", "app.notificacoes.email.enabled=false",
        "app.zapsign.webhook-secret=fluxo-test-secret"
})
@ActiveProfiles("test")
@Transactional
class RevisaoRespostaNotificacaoIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ContratoService contratos;
    @Autowired RevisaoDocumentoService revisoes;
    @Autowired NotificacaoRepository notificacoes;
    @MockitoBean EmailService email;
    @MockitoBean ZapSignClient zapSign;
    @MockitoBean DriveOrganizacaoService drive;
    private static final String COMENTARIO = "Rever os valores e as condições do documento.";
    private record Cenario(Proposta proposta, Contrato contrato, RevisaoDocumento revisao,
            Usuario comercial, Usuario juridico, Usuario tecnico, Usuario preparador, Usuario inativo) {}

    @ParameterizedTest
    @CsvSource({
            "PROPOSTA,AJUSTES_SOLICITADOS,PROPOSTA_REVISAO_SOLICITADA",
            "PROPOSTA,REPROVADO,PROPOSTA_REPROVADA",
            "CONTRATO,AJUSTES_SOLICITADOS,CONTRATO_REVISAO_SOLICITADA",
            "CONTRATO,REPROVADO,CONTRATO_REPROVADO"
    })
    void respostaPublicaNotificaSomenteOsDestinatariosCorretos(RevisaoDocumentoTipo documento, RevisaoDocumentoStatus status, String tipo) {
        var cenario = cenario(documento);
        responder(cenario.revisao(), status); em.flush(); em.clear();
        var recebidos = avisos(tipo);
        var destinatarios = documento == RevisaoDocumentoTipo.PROPOSTA ? List.of(cenario.comercial().getId())
                : List.of(cenario.comercial().getId(), cenario.juridico().getId());
        assertThat(recebidos).extracting(item -> item.getUsuario().getId()).containsExactlyInAnyOrderElementsOf(destinatarios);
        String destino = documento == RevisaoDocumentoTipo.PROPOSTA ? "/propostas?proposta=" + cenario.proposta().getIdProposta()
                : "/contratos?contrato=" + cenario.contrato().getIdContrato();
        assertThat(recebidos).allSatisfy(aviso -> {
            assertThat(aviso.getLida()).isFalse();
            assertThat(aviso.getDestino()).isEqualTo(destino);
            assertThat(aviso.getMensagem()).contains("versão 1", "Empresa teste", COMENTARIO);
        });
        assertThat(em.find(RevisaoDocumento.class, cenario.revisao().getId()).getStatus()).isEqualTo(status);
        if (documento == RevisaoDocumentoTipo.CONTRATO) {
            var contrato = em.find(Contrato.class, cenario.contrato().getIdContrato());
            assertThat(contrato.getStatus()).isEqualTo(status == RevisaoDocumentoStatus.REPROVADO ? "REJEITADO" : "PENDENTE");
            if (status == RevisaoDocumentoStatus.AJUSTES_SOLICITADOS) assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.REVISAO);
        } else assertThat(em.find(Proposta.class, cenario.proposta().getIdProposta()).getStatus())
                .isEqualTo(status == RevisaoDocumentoStatus.REPROVADO ? PropostaStatus.REJEITADA : PropostaStatus.PENDENTE);
        assertThatThrownBy(() -> responder(cenario.revisao(), status)).hasMessageContaining("já recebeu uma resposta");
        assertThat(avisos(tipo)).hasSize(destinatarios.size());
    }

    @ParameterizedTest
    @EnumSource(RevisaoDocumentoTipo.class)
    void novaVersaoGeraSeuProprioAvisoMesmoComComentarioIgual(RevisaoDocumentoTipo documento) {
        var cenario = cenario(documento);
        responder(cenario.revisao(), RevisaoDocumentoStatus.AJUSTES_SOLICITADOS);
        cenario.revisao().setStatus(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE);
        cenario.revisao().setVersaoAtual(2); versao(cenario.revisao(), 2);
        responder(cenario.revisao(), RevisaoDocumentoStatus.AJUSTES_SOLICITADOS); em.flush();
        String tipo = documento == RevisaoDocumentoTipo.PROPOSTA ? "PROPOSTA_REVISAO_SOLICITADA" : "CONTRATO_REVISAO_SOLICITADA";
        assertThat(avisos(tipo).stream().filter(item -> item.getUsuario().getId().equals(cenario.comercial().getId())))
                .hasSize(2).extracting(Notificacao::getMensagem)
                .anySatisfy(mensagem -> assertThat(mensagem).contains("versão 1"))
                .anySatisfy(mensagem -> assertThat(mensagem).contains("versão 2"));
    }

    @Test void assinaturaJaIniciadaNaoPermiteGerarAvisosDeResposta() {
        var cenario = cenario(RevisaoDocumentoTipo.CONTRATO);
        var versao = em.createQuery("select v from RevisaoDocumentoVersao v where v.revisao = :revisao", RevisaoDocumentoVersao.class)
                .setParameter("revisao", cenario.revisao()).getSingleResult();
        versao.setZapsignDocumentoToken("assinatura-iniciada");
        assertThatThrownBy(() -> responder(cenario.revisao(), RevisaoDocumentoStatus.AJUSTES_SOLICITADOS)).hasMessageContaining("assinatura deste contrato já foi iniciada");
        assertThatThrownBy(() -> responder(cenario.revisao(), RevisaoDocumentoStatus.REPROVADO)).hasMessageContaining("assinatura deste contrato já foi iniciada");
        assertThat(avisos("CONTRATO_REVISAO_SOLICITADA")).isEmpty();
        assertThat(avisos("CONTRATO_REPROVADO")).isEmpty();
    }

    private void responder(RevisaoDocumento revisao, RevisaoDocumentoStatus status) {
        if (status == RevisaoDocumentoStatus.REPROVADO) {
            revisoes.reprovar(revisao.getToken(), new RevisaoReprovacaoRequestDTO(COMENTARIO)); return;
        }
        var anotacao = new RevisaoAnotacaoRequestDTO(1, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.2"), new BigDecimal("0.1"), null, "Rever a cláusula marcada.");
        revisoes.enviarParaRevisao(revisao.getToken(), new RevisaoClienteRequestDTO(List.of(anotacao), COMENTARIO));
    }

    private List<Notificacao> avisos(String tipo) {
        return notificacoes.findAll().stream().filter(item -> tipo.equals(item.getTipo())).toList();
    }

    private Cenario cenario(RevisaoDocumentoTipo tipo) {
        var comercial = usuario("Comercial", null); var preparador = usuario("Preparador", null); var tecnico = usuario("Técnico", null);
        Cargo cargo = new Cargo(); cargo.setNome("Diretor Jurídico"); em.persist(cargo);
        var juridico = usuario("Jurídico", cargo); var inativo = usuario("Jurídico inativo", cargo); inativo.setSituacao("INATIVO");
        Empresa empresa = new Empresa(); empresa.setRazaoSocial("Empresa teste"); empresa.setNomeFantasia("Empresa teste");
        empresa.setCnpj("11.222.333/0001-81"); empresa.setEmail("cliente@example.test"); em.persist(empresa);
        Proposta proposta = new Proposta(); proposta.setUsuario(comercial); proposta.setEmpresa(empresa); proposta.setServico(ServicoComercial.CFO);
        proposta.setStatus(tipo == RevisaoDocumentoTipo.PROPOSTA ? PropostaStatus.PENDENTE : PropostaStatus.APROVADA); em.persist(proposta);
        Contrato contrato = tipo == RevisaoDocumentoTipo.CONTRATO ? contratos.criarPreparacaoDoPipeline(proposta, preparador, comercial, tecnico) : null;
        var revisao = new RevisaoDocumento(); revisao.setTipo(tipo);
        revisao.setReferenciaId(contrato == null ? proposta.getIdProposta() : contrato.getIdContrato());
        revisao.setEmpresa(empresa); revisao.setEmpresaNome(empresa.getNomeFantasia()); revisao.setDestinatarioEmail(empresa.getEmail());
        revisao.setToken(UUID.randomUUID().toString()); revisao.setTokenExpiraEm(LocalDateTime.now().plusDays(1));
        revisao.setStatus(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE); revisao.setVersaoAtual(1); revisao.setEmailStatus("ENVIADO");
        revisao.setCriadoEm(LocalDateTime.now()); revisao.setAtualizadoEm(LocalDateTime.now()); em.persist(revisao); versao(revisao, 1);
        return new Cenario(proposta, contrato, revisao, comercial, juridico, tecnico, preparador, inativo);
    }

    private void versao(RevisaoDocumento revisao, int numero) {
        var versao = new RevisaoDocumentoVersao(); versao.setRevisao(revisao); versao.setNumero(numero); versao.setNomeArquivo("documento.pdf");
        versao.setArquivoUrl("https://arquivo.test/documento.pdf"); versao.setContentType("application/pdf"); versao.setTotalPaginas(1);
        versao.setResultado(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE); versao.setCriadoEm(LocalDateTime.now()); em.persist(versao);
    }

    private Usuario usuario(String nome, Cargo cargo) {
        var usuario = new Usuario(); usuario.setNomeCompleto(nome); usuario.setEmail(UUID.randomUUID() + "@resposta.test");
        usuario.setSituacao("ATIVO"); usuario.setSenhaHash("test"); usuario.setContato("71999999999"); usuario.setCargo(cargo);
        em.persist(usuario); return usuario;
    }
}
