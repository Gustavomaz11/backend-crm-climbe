package com.climb.api.service;

import com.climb.api.config.ZapSignProperties;
import com.climb.api.model.*;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.model.dto.RevisaoAnotacaoRequestDTO;
import com.climb.api.model.dto.RevisaoClienteRequestDTO;
import com.climb.api.model.dto.RevisaoReprovacaoRequestDTO;
import com.climb.api.model.enums.ContratoPreparacaoEtapa;
import com.climb.api.model.enums.RevisaoDocumentoStatus;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevisaoContratoEnvioTest {
    private final RevisaoDocumentoRepository revisoes = mock(RevisaoDocumentoRepository.class);
    private final RevisaoDocumentoVersaoRepository versoes = mock(RevisaoDocumentoVersaoRepository.class);
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final ContratoNotificacaoService notificacoes = mock(ContratoNotificacaoService.class);
    private final ZapSignClient zapSign = mock(ZapSignClient.class);
    private final CloudflareR2ArquivoStorageService storage = mock(CloudflareR2ArquivoStorageService.class);
    private final DocumentoPreviewService preview = mock(DocumentoPreviewService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RbacService rbac = mock(RbacService.class);
    private final List<RevisaoDocumentoVersao> criadas = new ArrayList<>();
    private RevisaoDocumentoService service;
    private Contrato contrato;

    @BeforeEach
    void setup() {
        var properties = new ZapSignProperties(); properties.setWebhookSecret("test-secret");
        service = new RevisaoDocumentoService(revisoes, versoes, mock(RevisaoDocumentoAnotacaoRepository.class),
                mock(PropostaRepository.class), contratos, usuarios, storage, preview,
                email, notificacoes, new ContratoParcelaCalculator(), rbac, zapSign,
                properties, "http://app.test", 30);
        contrato = new Contrato(); contrato.setIdContrato(10L);
        Empresa empresa = new Empresa(); empresa.setNomeFantasia("Empresa"); empresa.setEmail("cliente@example.test");
        contrato.setEmpresa(empresa);
        when(revisoes.save(any())).thenAnswer(i -> { RevisaoDocumento r = i.getArgument(0); r.setId(1L); return r; });
        when(versoes.save(any())).thenAnswer(i -> {
            RevisaoDocumentoVersao v = i.getArgument(0);
            if (!criadas.contains(v)) { v.setId((long) criadas.size() + 1); criadas.add(v); }
            return v;
        });
        when(versoes.findByRevisaoIdOrderByNumeroDesc(1L)).thenAnswer(i -> criadas);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveMoverParaRevisaoSomenteQuandoClienteSolicitaAjustes(boolean legado) {
        contrato.setEtapaPreparacao(legado ? null : ContratoPreparacaoEtapa.EM_ANDAMENTO);
        RevisaoDocumento revisao = iniciarRevisaoInterativa();
        var anotacao = new RevisaoAnotacaoRequestDTO(1, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.2"), new BigDecimal("0.1"), "#FACC15", "Ajustar esta cláusula");
        var response = service.enviarParaRevisao(revisao.getToken(),
                new RevisaoClienteRequestDTO(List.of(anotacao), "Por favor, ajustar a cláusula marcada."));
        assertThat(response.status()).isEqualTo("AJUSTES_SOLICITADOS");
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.REVISAO);
        verify(contratos).save(contrato);
    }

    @Test
    void deveVoltarParaEmAndamentoAoEnviarNovaVersaoAoCliente() {
        contrato.setEtapaPreparacao(ContratoPreparacaoEtapa.REVISAO);
        RevisaoDocumento revisao = iniciarRevisaoInterativa();
        revisao.setStatus(RevisaoDocumentoStatus.AJUSTES_SOLICITADOS);
        when(revisoes.findById(1L)).thenReturn(Optional.of(revisao));
        when(rbac.temPermissao(1L, PermissaoCodigo.CONTRATO_CRUD)).thenReturn(true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        var arquivo = new MockMultipartFile("arquivo", "contrato-v2.pdf", "application/pdf", new byte[]{1});
        when(storage.salvar(arquivo, "revisoes/contrato-10")).thenReturn(new ArquivoUploadResponseDTO(
                "contrato-v2.pdf", "application/pdf", 1, "key-v2", "http://storage.test/contrato-v2.pdf"));
        var response = service.novaVersao(1L, 1L, arquivo);
        assertThat(response.status()).isEqualTo("AGUARDANDO_CLIENTE");
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.EM_ANDAMENTO);
        assertThat(contrato.getUrlPdf()).endsWith("contrato-v2.pdf");
    }

    @Test
    void naoDeveConfundirReprovacaoComSolicitacaoDeRevisao() {
        contrato.setEtapaPreparacao(ContratoPreparacaoEtapa.EM_ANDAMENTO);
        RevisaoDocumento revisao = iniciarRevisaoInterativa();
        service.reprovar(revisao.getToken(), new RevisaoReprovacaoRequestDTO("Não vamos prosseguir."));
        assertThat(contrato.getStatus()).isEqualTo("REJEITADO");
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.EM_ANDAMENTO);
    }

    private RevisaoDocumento iniciarRevisaoInterativa() {
        when(contratos.findById(10L)).thenReturn(Optional.of(contrato));
        when(storage.baixar(anyString())).thenReturn(new byte[]{1});
        when(preview.totalPaginas(any(), eq("application/pdf"))).thenReturn(1);
        service.iniciarContrato(contrato,
                new ArquivoUploadResponseDTO("contrato.pdf", "application/pdf", 1, "key", "http://storage.test/contrato.pdf"), new Usuario());
        RevisaoDocumento revisao = criadas.getFirst().getRevisao();
        when(revisoes.findByToken(revisao.getToken())).thenReturn(Optional.of(revisao));
        when(versoes.findByRevisaoIdAndNumero(1L, 1)).thenReturn(Optional.of(criadas.getFirst()));
        return revisao;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveNotificarComercialSomenteQuandoEmailFoiEnviado(boolean enviado) {
        when(email.enviarEmailComBotao(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(enviado);
        when(contratos.findById(10L)).thenReturn(Optional.of(contrato));
        var response = service.iniciarContrato(contrato,
                new ArquivoUploadResponseDTO("contrato.pdf", "application/pdf", 1, "key", "http://storage.test/contrato.pdf"), new Usuario());
        assertThat(response.emailStatus()).isEqualTo(enviado ? "ENVIADO" : "FALHOU");
        assertThat(response.versoes()).hasSize(1);
        verify(notificacoes, times(enviado ? 1 : 0)).notificarContratoEnviado(contrato);
        verify(email).enviarEmailComBotao(eq("cliente@example.test"), anyString(), anyString(), anyString(), anyString(), startsWith("http://app.test/revisao/"), anyString());
    }

    @org.junit.jupiter.api.Test
    void deveConcluirPreparacaoEGerarParcelasSomenteAposAssinaturaConfirmada() {
        Usuario tecnico = new Usuario(); tecnico.setId(2L); tecnico.setEmail("tecnico@example.test");
        Usuario comercial = new Usuario(); comercial.setId(1L); comercial.setEmail("comercial@example.test");
        contrato.setResponsavel(tecnico); contrato.setResponsavelComercial(comercial);
        contrato.setEtapaPreparacao(com.climb.api.model.enums.ContratoPreparacaoEtapa.REVISAO);
        contrato.setStatus("PENDENTE");
        Proposta proposta = new Proposta(); proposta.setValuation(new java.math.BigDecimal("50000.00"));
        proposta.setServico(com.climb.api.model.enums.ServicoComercial.VALUATION); proposta.setQuantidadeParcelas(2);
        contrato.setProposta(proposta);
        when(contratos.findById(10L)).thenReturn(Optional.of(contrato));
        service.iniciarContrato(contrato,
                new ArquivoUploadResponseDTO("contrato.pdf", "application/pdf", 1, "key", "http://storage.test/contrato.pdf"), comercial);
        var captor = org.mockito.ArgumentCaptor.forClass(RevisaoDocumentoVersao.class);
        verify(versoes).save(captor.capture());
        var versao = captor.getValue(); versao.setZapsignDocumentoToken("document-token");
        when(versoes.findByZapsignDocumentoToken("document-token")).thenReturn(Optional.of(versao));
        when(zapSign.detalharDocumento("document-token")).thenReturn(new ZapSignClient.Documento("document-token", "signed", List.of()));
        service.processarWebhookZapSign("test-secret", new com.climb.api.model.dto.ZapSignWebhookRequestDTO("doc_signed", "document-token", "signed", null));
        assertThat(contrato.getStatus()).isEqualTo("APROVADO");
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(com.climb.api.model.enums.ContratoPreparacaoEtapa.CONCLUIDO);
        assertThat(contrato.getParcelas()).hasSize(2);
        assertThat(contrato.getParcelas()).extracting(ContratoParcela::getValor)
                .containsExactly(new java.math.BigDecimal("25000.00"), new java.math.BigDecimal("25000.00"));
        verify(email).enviarEmailComBotao(eq("tecnico@example.test"), anyString(), anyString(), anyString(),
                eq("Abrir contrato"), eq("http://app.test/kanban?contrato=10"), anyString());
        service.processarWebhookZapSign("test-secret", new com.climb.api.model.dto.ZapSignWebhookRequestDTO("doc_signed", "document-token", "signed", null));
        assertThat(contrato.getParcelas()).hasSize(2);
    }
}
