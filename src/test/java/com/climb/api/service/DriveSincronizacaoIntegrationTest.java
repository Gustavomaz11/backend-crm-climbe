package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.mail.host=localhost", "logging.level.root=WARN",
        "app.cnpja.comprovantes-interval-ms=86400000", "app.pipeline.cadencia.atraso-inicial-ms=86400000",
        "app.pipeline.task-reminders.weekly-cron=-", "app.pipeline.task-reminders.overdue-cron=-"})
@ActiveProfiles("test") @Transactional
class DriveSincronizacaoIntegrationTest {
    @Autowired EmpresaRepository empresas;
    @Autowired EmpresaDriveRepository filas;
    @Autowired EmpresaDriveServicoRepository servicos;
    @Autowired EmpresaComprovanteRepository comprovantes;
    @Autowired DriveOrganizacaoService organizacao;
    @Autowired DriveSincronizacaoService sincronizacao;
    @MockitoBean GoogleDriveClient drive;

    @Test void criaEmpresaDocumentacoesETodosOsServicosDaPropostaSemDuplicarPorContrato() {
        var empresa = empresa();
        var contrato = new Contrato(); contrato.setEmpresa(empresa); contrato.setServico(ServicoComercial.BPO);
        var proposta = new Proposta(); proposta.setServicos(List.of(
                new PropostaServico(ServicoComercial.BPO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
                new PropostaServico(ServicoComercial.CFO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)));
        contrato.setProposta(proposta);
        organizacao.agendarContrato(contrato); organizacao.agendarContrato(contrato);
        assertThat(servicos.findByEmpresaIdOrderByIdAsc(empresa.getIdEmpresa())).hasSize(2);
        servicos.findByEmpresaIdOrderByIdAsc(empresa.getIdEmpresa()).forEach(pasta ->
                pasta.setPermissoesTentarEm(LocalDateTime.now().plusHours(1)));
        when(drive.garantirPasta(anyString(), anyString(), anyString())).thenAnswer(i -> "pasta-" + i.getArgument(2));
        sincronizacao.sincronizar(empresa.getIdEmpresa());
        verify(drive).garantirPasta("1Ea1yJuqRd7jfSAa_aC58qyl_Dsv9qmVB", "Empresa-37.335.118/0001-80", "empresa-" + empresa.getIdEmpresa());
        verify(drive).garantirPasta("pasta-empresa-" + empresa.getIdEmpresa(), "Documentações", "documentacoes-" + empresa.getIdEmpresa());
        assertThat(servicos.findByEmpresaIdOrderByIdAsc(empresa.getIdEmpresa())).allMatch(pasta -> pasta.getPastaId() != null);
        assertThat(servicos.findByEmpresaIdOrderByIdAsc(empresa.getIdEmpresa()))
                .allMatch(pasta -> !pasta.getPermissoesTentarEm().isAfter(LocalDateTime.now()));
        assertThat(filas.findById(empresa.getIdEmpresa()).orElseThrow().isPendente()).isFalse();
        sincronizacao.sincronizar(empresa.getIdEmpresa());
        verify(drive, times(4)).garantirPasta(anyString(), anyString(), anyString());
    }

    @Test void reenviaPdfJaEmitidoAposFalhaDoDriveSemPerderConteudoNemReemitirNoCnpja() {
        var empresa = empresa(); var comprovante = new EmpresaComprovante();
        comprovante.setEmpresaId(empresa.getIdEmpresa()); comprovante.setCnpj("37335118000180");
        comprovante.setTipo("RFB"); comprovante.setStatus("DISPONIVEL"); comprovante.setAtualizadoEm(LocalDateTime.now());
        comprovante.setConteudo(new byte[]{1, 2, 3}); comprovantes.saveAndFlush(comprovante);
        organizacao.agendarEmpresa(empresa.getIdEmpresa());
        when(drive.garantirPasta(anyString(), anyString(), anyString())).thenReturn("pasta");
        when(drive.enviar(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Drive indisponível"))
                .thenReturn(new GoogleDriveClient.Arquivo("pdf", "RFB.pdf", "application/pdf", 3, List.of("pasta"), false, null));
        sincronizacao.sincronizar(empresa.getIdEmpresa());
        var fila = filas.findById(empresa.getIdEmpresa()).orElseThrow();
        assertThat(fila.isPendente()).isTrue(); assertThat(fila.getErro()).isEqualTo("Drive indisponível");
        assertThat(comprovante.getStatus()).isEqualTo("DISPONIVEL"); assertThat(comprovante.getConteudo()).containsExactly(1, 2, 3);
        sincronizacao.sincronizar(empresa.getIdEmpresa());
        verify(drive, times(1)).enviar(anyString(), anyString(), anyString(), any(), anyString());
        fila.setTentarEm(LocalDateTime.now().minusSeconds(1)); filas.saveAndFlush(fila);
        sincronizacao.sincronizar(empresa.getIdEmpresa());
        assertThat(comprovantes.findById(comprovante.getId()).orElseThrow().getDriveArquivoId()).isEqualTo("pdf");
        assertThat(fila.isPendente()).isFalse();
        organizacao.agendarEmpresa(empresa.getIdEmpresa()); sincronizacao.sincronizar(empresa.getIdEmpresa());
        verify(drive, times(2)).enviar(anyString(), eq("RFB-37335118000180.pdf"), eq("application/pdf"), any(), anyString());
    }

    private Empresa empresa() {
        var empresa = new Empresa(); empresa.setNomeFantasia("Empresa"); empresa.setRazaoSocial("Empresa Ltda.");
        empresa.setCnpj("37.335.118/0001-80"); return empresas.saveAndFlush(empresa);
    }
}
