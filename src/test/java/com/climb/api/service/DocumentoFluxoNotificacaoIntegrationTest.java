package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PropostaAprovacaoRequestDTO;
import com.climb.api.model.dto.ZapSignWebhookRequestDTO;
import com.climb.api.model.enums.*;
import com.climb.api.repository.NotificacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false", "spring.mail.host=localhost", "logging.level.root=WARN", "debug=false",
        "app.pipeline.cadencia.atraso-inicial-ms=86400000", "app.notificacoes.email.enabled=false",
        "app.zapsign.webhook-secret=fluxo-test-secret"
})
@ActiveProfiles("test")
@Transactional
class DocumentoFluxoNotificacaoIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ContratoService contratos;
    @Autowired PropostaService propostas;
    @Autowired RevisaoDocumentoService revisoes;
    @Autowired DocumentoFluxoNotificacaoService avisos;
    @Autowired NotificacaoRepository notificacoes;
    @MockitoBean EmailService email;
    @MockitoBean ZapSignClient zapSign;
    @MockitoBean DriveOrganizacaoService drive;

    @Test void aceitePublicoDaPropostaGeraAvisoPersistidoParaOComercial() {
        Usuario comercial = usuario("Comercial", null);
        Proposta proposta = proposta(comercial); proposta.setStatus(PropostaStatus.PENDENTE);
        var versao = revisao(RevisaoDocumentoTipo.PROPOSTA, proposta.getIdProposta(), proposta.getEmpresa());
        revisoes.aprovar(versao.getRevisao().getToken());
        em.flush(); em.clear();
        assertThat(em.find(Proposta.class, proposta.getIdProposta()).getStatus()).isEqualTo(PropostaStatus.APROVADA);
        assertThat(avisos(comercial, "PROPOSTA_ACEITA")).singleElement().satisfies(aviso -> {
            assertThat(aviso.getLida()).isFalse();
            assertThat(aviso.getMensagem()).contains("versão 1", "Empresa teste");
            assertThat(aviso.getDestino()).isEqualTo("/propostas?proposta=" + proposta.getIdProposta());
        });
        assertThatThrownBy(() -> revisoes.aprovar(versao.getRevisao().getToken())).hasMessageContaining("já recebeu");
        assertThat(avisos(comercial, "PROPOSTA_ACEITA")).hasSize(1);
    }

    @Test void contratoDaPipelineNotificaComercialEJuridicoSemAnteciparAvisoAosTecnicos() {
        Usuario comercial = usuario("Comercial", null), preparador = usuario("Preparador", null), tecnico = usuario("Técnico", null);
        Usuario juridico = usuario("Jurídico", cargo("Diretor Jurídico", true));
        Usuario juridicoSemAcento = usuario("Assessor", cargo("Assessor Juridico", true));
        Usuario inativo = usuario("Inativo", juridico.getCargo()); inativo.setSituacao("INATIVO");
        Usuario cargoInativo = usuario("Cargo inativo", cargo("Advogado Jurídico", false));
        Usuario leitor = usuario("Leitor de contratos", null);
        Permissao permissao = new Permissao(); permissao.setCodigo("JURIDICO_CONTRATO_LER"); em.persist(permissao);
        leitor.getPermissoes().add(permissao);
        Proposta proposta = proposta(comercial);
        Contrato contrato = contratos.criarPreparacaoDoPipeline(proposta, preparador, comercial, tecnico);
        em.flush();
        for (Usuario destinatario : List.of(comercial, juridico, juridicoSemAcento)) {
            assertThat(avisos(destinatario, "CONTRATO_CRIADO")).singleElement()
                    .satisfies(aviso -> assertThat(aviso.getDestino()).isEqualTo("/contratos?contrato=" + contrato.getIdContrato()));
        }
        for (Usuario semAviso : List.of(preparador, tecnico, inativo, cargoInativo, leitor)) {
            assertThat(notificacoes.findByUsuario_Id(semAviso.getId())).isEmpty();
        }
        verifyNoInteractions(email);
    }

    @Test void assinaturaNotificaTodosOsTecnicosComercialEJuridicoUmaVez() {
        Usuario comercial = usuario("Comercial", null), contabil = usuario("Contábil", null), cfo = usuario("CFO", null);
        Usuario juridico = usuario("Jurídico", cargo("Diretor Jurídico", true));
        Proposta proposta = proposta(comercial);
        Contrato contrato = contratos.criarPreparacaoDoPipeline(proposta, comercial, comercial, contabil,
                Map.of(ServicoComercial.CONTABILIDADE, contabil, ServicoComercial.CFO, cfo));
        var versao = revisao(RevisaoDocumentoTipo.CONTRATO, contrato.getIdContrato(), proposta.getEmpresa());
        when(zapSign.detalharDocumento(versao.getZapsignDocumentoToken()))
                .thenReturn(new ZapSignClient.Documento(versao.getZapsignDocumentoToken(), "signed", List.of()));
        var webhook = new ZapSignWebhookRequestDTO("doc_signed", versao.getZapsignDocumentoToken(), "signed", null);
        revisoes.processarWebhookZapSign("fluxo-test-secret", webhook);
        revisoes.processarWebhookZapSign("fluxo-test-secret", webhook); em.flush();
        for (Usuario destinatario : List.of(comercial, contabil, cfo, juridico)) {
            assertThat(avisos(destinatario, "CONTRATO_APROVADO")).singleElement()
                    .satisfies(aviso -> assertThat(aviso.getLida()).isFalse());
        }
        assertThat(avisos(contabil, "CONTRATO_APROVADO").getFirst().getDestino()).endsWith("&servico=CONTABILIDADE");
        assertThat(avisos(cfo, "CONTRATO_APROVADO").getFirst().getDestino()).endsWith("&servico=CFO");
        assertThat(avisos(juridico, "CONTRATO_APROVADO").getFirst().getDestino()).isEqualTo("/contratos?contrato=" + contrato.getIdContrato());
        assertThat(contrato.getStatus()).isEqualTo("APROVADO");
        verify(drive, times(1)).agendarContrato(contrato);
    }

    @Test void papeisAcumuladosENovosDisparosNaoDuplicamAvisoMasNovaVersaoNotifica() {
        Usuario pessoa = usuario("Comercial e jurídico", cargo("Diretor Jurídico", true));
        Contrato contrato = contratos.criarPreparacaoDoPipeline(proposta(pessoa), pessoa, pessoa, pessoa);
        contrato.getResponsaveisPorServico().put(ServicoComercial.CFO, pessoa);
        avisos.contratoAprovado(contrato, 1); avisos.contratoAprovado(contrato, 1); em.flush();
        assertThat(avisos(pessoa, "CONTRATO_CRIADO")).hasSize(1);
        assertThat(avisos(pessoa, "CONTRATO_APROVADO")).hasSize(1);
        em.clear(); contrato = em.find(Contrato.class, contrato.getIdContrato());
        avisos.contratoAprovado(contrato, 1);
        assertThat(avisos(pessoa, "CONTRATO_APROVADO")).hasSize(1);
        avisos.contratoAprovado(contrato, 2); em.flush();
        assertThat(avisos(pessoa, "CONTRATO_APROVADO")).hasSize(2);
    }

    @Test void aprovacaoInternaDePropostaEContratoTambemNotificaSemRepetir() {
        Usuario comercial = usuario("Comercial", null), tecnico = usuario("Técnico", null);
        Usuario juridico = usuario("Jurídico", cargo("Diretor Jurídico", true));
        Permissao permissao = new Permissao(); permissao.setCodigo("JURIDICO_CONTRATO_APROVAR"); em.persist(permissao);
        juridico.getPermissoes().add(permissao);
        Proposta proposta = proposta(comercial); proposta.setStatus(PropostaStatus.PENDENTE);
        propostas.aprovar(proposta.getIdProposta(), comercial.getId(), new PropostaAprovacaoRequestDTO(PropostaStatus.APROVADA));
        propostas.aprovar(proposta.getIdProposta(), comercial.getId(), new PropostaAprovacaoRequestDTO(PropostaStatus.APROVADA));
        assertThat(avisos(comercial, "PROPOSTA_ACEITA")).hasSize(1);
        Contrato contrato = contratos.criarPreparacaoDoPipeline(proposta, comercial, comercial, tecnico);
        contrato.setUrlPdf("https://arquivo.test/contrato.pdf");
        contratos.aprovar(contrato.getIdContrato(), juridico.getId(), "APROVADO");
        contratos.aprovar(contrato.getIdContrato(), juridico.getId(), "APROVADO");
        for (Usuario destinatario : List.of(comercial, tecnico, juridico)) {
            assertThat(avisos(destinatario, "CONTRATO_APROVADO")).hasSize(1);
        }
    }

    private List<Notificacao> avisos(Usuario usuario, String tipo) {
        return notificacoes.findByUsuario_Id(usuario.getId()).stream().filter(item -> tipo.equals(item.getTipo())).toList();
    }

    private Usuario usuario(String nome, Cargo cargo) {
        Usuario usuario = new Usuario(); usuario.setNomeCompleto(nome); usuario.setEmail(UUID.randomUUID() + "@fluxo.test");
        usuario.setSituacao("ATIVO"); usuario.setSenhaHash("test"); usuario.setContato("71999999999"); usuario.setCargo(cargo);
        em.persist(usuario); return usuario;
    }

    private Cargo cargo(String nome, boolean ativo) {
        Cargo cargo = new Cargo(); cargo.setNome(nome); cargo.setAtivo(ativo); em.persist(cargo); return cargo;
    }

    private Proposta proposta(Usuario comercial) {
        Empresa empresa = new Empresa(); empresa.setRazaoSocial("Empresa teste"); empresa.setNomeFantasia("Empresa teste");
        empresa.setCnpj("11.222.333/0001-81"); empresa.setEmail("cliente@example.test"); em.persist(empresa);
        Proposta proposta = new Proposta(); proposta.setEmpresa(empresa); proposta.setUsuario(comercial); proposta.setStatus(PropostaStatus.APROVADA);
        proposta.setServicos(List.of(new PropostaServico(ServicoComercial.CONTABILIDADE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)));
        em.persist(proposta); return proposta;
    }

    private RevisaoDocumentoVersao revisao(RevisaoDocumentoTipo tipo, Long referenciaId, Empresa empresa) {
        var revisao = new RevisaoDocumento(); revisao.setTipo(tipo); revisao.setReferenciaId(referenciaId); revisao.setEmpresa(empresa);
        revisao.setEmpresaNome(empresa.getNomeFantasia()); revisao.setDestinatarioEmail(empresa.getEmail());
        revisao.setToken(UUID.randomUUID().toString()); revisao.setTokenExpiraEm(LocalDateTime.now().plusDays(1));
        revisao.setStatus(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE); revisao.setVersaoAtual(1); revisao.setEmailStatus("ENVIADO");
        revisao.setCriadoEm(LocalDateTime.now()); revisao.setAtualizadoEm(LocalDateTime.now()); em.persist(revisao);
        var versao = new RevisaoDocumentoVersao(); versao.setRevisao(revisao); versao.setNumero(1); versao.setNomeArquivo("documento.pdf");
        versao.setArquivoUrl("https://arquivo.test/documento.pdf"); versao.setContentType("application/pdf"); versao.setTotalPaginas(1);
        versao.setResultado(RevisaoDocumentoStatus.AGUARDANDO_CLIENTE); versao.setCriadoEm(LocalDateTime.now());
        if (tipo == RevisaoDocumentoTipo.CONTRATO) versao.setZapsignDocumentoToken(UUID.randomUUID().toString());
        em.persist(versao); return versao;
    }
}
