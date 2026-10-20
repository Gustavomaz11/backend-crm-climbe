package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.Empresa;
import com.climb.api.model.Proposta;
import com.climb.api.model.Usuario;
import com.climb.api.repository.ContratoKanbanTaskRepository;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.HistoricoAprovacaoContratoRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.climb.api.model.enums.ContratoPreparacaoEtapa;
import com.climb.api.model.PermissaoCodigo;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ContratoServiceTest {

    @Mock private ContratoRepository repository;
    @Mock private ContratoKanbanTaskRepository taskRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private HistoricoAprovacaoContratoRepository historicoRepository;
    @Mock private ContratoNotificacaoService contratoNotificacaoService;
    @Mock private CloudflareR2ArquivoStorageService arquivoStorageService;
    @Mock private RbacService rbacService;
    @Mock private RevisaoDocumentoService revisaoDocumentoService;
    @Mock private ContratoParcelaCalculator parcelaCalculator;

    private ContratoService service;

    @Test
    void edicaoJuridicaNaoPermiteAprovarPelaAtualizacaoGenerica() {
        when(rbacService.temPermissao(eq(7L), any(PermissaoCodigo.class)))
                .thenAnswer(call -> call.getArgument(1) == PermissaoCodigo.JURIDICO_CONTRATO_EDITAR);
        var atual = new Contrato(); atual.setStatus("PENDENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(atual));
        var atualizado = new Contrato(); atualizado.setStatus("APROVADO");
        assertThat(assertThrows(ResponseStatusException.class, () -> service.atualizar(1L, atualizado, 7L))
                .getStatusCode().value()).isEqualTo(403);
        verify(repository, never()).save(any());
        verifyNoInteractions(contratoNotificacaoService);
    }

    @Test
    void edicaoJuridicaNaoPermiteRemoverParticipantesPelaAtualizacaoGenerica() {
        when(rbacService.temPermissao(eq(7L), any(PermissaoCodigo.class)))
                .thenAnswer(call -> call.getArgument(1) == PermissaoCodigo.JURIDICO_CONTRATO_EDITAR);
        var responsavel = new Usuario(); responsavel.setId(8L);
        var participante = new Usuario(); participante.setId(9L);
        var atual = new Contrato(); atual.setStatus("PENDENTE"); atual.setResponsavel(responsavel);
        atual.setParticipantes(Set.of(participante));
        when(repository.findById(1L)).thenReturn(Optional.of(atual));
        var atualizado = new Contrato(); atualizado.setResponsavel(responsavel);
        assertThat(assertThrows(ResponseStatusException.class, () -> service.atualizar(1L, atualizado, 7L))
                .getStatusCode().value()).isEqualTo(403);
        verify(repository, never()).save(any());
    }

    @Test
    void criacaoJuridicaComUploadExigeTambemEnvioAoCliente() {
        when(rbacService.temPermissao(eq(7L), any(PermissaoCodigo.class)))
                .thenAnswer(call -> call.getArgument(1) == PermissaoCodigo.JURIDICO_CONTRATO_CRIAR);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.criarComArquivo(1L, null, 7L, 8L, java.util.List.of(8L), null))
                .getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(arquivoStorageService, revisaoDocumentoService, contratoNotificacaoService);
    }

    @BeforeEach
    void setUp() {
        service = new ContratoService(
                repository,
                taskRepository,
                propostaRepository,
                empresaRepository,
                usuarioRepository,
                historicoRepository,
                contratoNotificacaoService,
                arquivoStorageService,
                rbacService,
                revisaoDocumentoService,
                parcelaCalculator,
                30
        );
    }

    @Test
    void deveRemoverParticipanteDuplicadoQuandoTambemForResponsavel() {
        Usuario usuarioPersistido = new Usuario();
        usuarioPersistido.setId(1L);

        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1L);
        empresa.setNomeFantasia("Apex Ventures");

        Proposta propostaPersistida = new Proposta();
        propostaPersistida.setIdProposta(1L);
        propostaPersistida.setUsuario(usuarioPersistido);
        propostaPersistida.setEmpresa(empresa);

        Usuario responsavelRecebido = new Usuario();
        responsavelRecebido.setId(1L);
        Usuario participanteRecebido = new Usuario();
        participanteRecebido.setId(1L);

        Proposta propostaRecebida = new Proposta();
        propostaRecebida.setIdProposta(1L);

        Contrato contrato = new Contrato();
        contrato.setProposta(propostaRecebida);
        contrato.setResponsavel(responsavelRecebido);
        contrato.setParticipantes(new HashSet<>(Set.of(participanteRecebido)));
        contrato.setStatus(ContratoService.STATUS_PENDENTE);

        when(repository.existsByProposta_IdProposta(1L)).thenReturn(false);
        when(propostaRepository.findById(1L)).thenReturn(Optional.of(propostaPersistida));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioPersistido));
        when(repository.save(any(Contrato.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Contrato salvo = service.criar(contrato);

        assertThat(salvo.getParticipantes())
                .containsExactly(usuarioPersistido);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.climb.api.model.enums.ServicoComercial.class)
    void deveCriarPreparacaoComPropostaETecnicoSemNotificarTecnico(com.climb.api.model.enums.ServicoComercial servico) {
        Proposta proposta = new Proposta(); proposta.setIdProposta(10L); proposta.setServico(servico);
        Empresa empresa = new Empresa(); empresa.setNomeFantasia("Empresa"); proposta.setEmpresa(empresa);
        when(propostaRepository.findById(10L)).thenReturn(Optional.of(proposta));
        Usuario comercial = new Usuario(); comercial.setId(1L);
        Usuario tecnico = new Usuario(); tecnico.setId(2L);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        Contrato contrato = service.criarPreparacaoDoPipeline(proposta, comercial, comercial, tecnico);
        assertThat(contrato.getProposta()).isSameAs(proposta);
        assertThat(contrato.getServico()).isEqualTo(servico);
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.A_FAZER);
        assertThat(contrato.getResponsavelComercial()).isSameAs(comercial);
        assertThat(contrato.getResponsavel()).isSameAs(tecnico);
        assertThat(contrato.getUrlPdf()).isNull();
        verify(contratoNotificacaoService).notificarPreparacaoCriada(contrato);
        verify(contratoNotificacaoService, never()).notificarContratoAprovado(any());
        verifyNoInteractions(revisaoDocumentoService);
    }

    @Test
    void deveEnviarArquivoNoMesmoContratoEAvancarParaEmAndamento() {
        Contrato contrato = rascunho();
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        when(rbacService.temPermissao(1L, PermissaoCodigo.CONTRATO_EDITAR)).thenReturn(true);
        Usuario usuario = new Usuario(); usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        var arquivo = new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf", new byte[]{1});
        var upload = new com.climb.api.model.dto.ArquivoUploadResponseDTO("contrato.pdf", "application/pdf", 1L, "key", "https://storage.test/contrato.pdf");
        when(arquivoStorageService.salvar(arquivo, "contratos/empresa-30")).thenReturn(upload);
        service.enviarAoCliente(10L, 1L, arquivo);
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.EM_ANDAMENTO);
        assertThat(contrato.getUrlPdf()).isEqualTo(upload.url());
        verify(repository).save(contrato);
        verify(revisaoDocumentoService).iniciarContrato(contrato, upload, usuario);
    }

    @Test
    void deveCriarContratoComArquivoEmAndamento() {
        Empresa empresa = rascunho().getEmpresa();
        Usuario usuario = new Usuario(); usuario.setId(1L);
        when(rbacService.temPermissao(1L, PermissaoCodigo.CONTRATO_CRIAR)).thenReturn(true);
        when(empresaRepository.findById(30L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var arquivo = new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf", new byte[]{1});
        var upload = new com.climb.api.model.dto.ArquivoUploadResponseDTO(
                "contrato.pdf", "application/pdf", 1L, "key", "https://storage.test/contrato.pdf");
        when(arquivoStorageService.salvar(arquivo, "contratos/empresa-30")).thenReturn(upload);
        Contrato salvo = service.criarComArquivo(30L, null, 1L, 1L, java.util.List.of(1L), arquivo);
        assertThat(salvo.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.EM_ANDAMENTO);
        verify(revisaoDocumentoService).iniciarContrato(salvo, upload, usuario);
    }

    @Test
    void deveRecusarMovimentacaoManualParaRevisaoMesmoComArquivoEnviado() {
        Contrato contrato = rascunho();
        contrato.setUrlPdf("https://storage.test/contrato.pdf");
        contrato.setEtapaPreparacao(ContratoPreparacaoEtapa.EM_ANDAMENTO);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        when(rbacService.temPermissao(1L, PermissaoCodigo.CONTRATO_EDITAR)).thenReturn(true);
        var erro = assertThrows(ResponseStatusException.class,
                () -> service.moverPreparacao(10L, 1L, ContratoPreparacaoEtapa.REVISAO));
        assertThat(erro.getReason()).contains("cliente");
        assertThat(contrato.getEtapaPreparacao()).isEqualTo(ContratoPreparacaoEtapa.EM_ANDAMENTO);
        verify(repository, never()).save(any());
    }

    @Test
    void deveRecusarUploadDuplicadoEUploadSemPermissao() {
        Contrato contrato = rascunho(); contrato.setUrlPdf("https://storage.test/existente.pdf");
        var arquivo = new MockMultipartFile("arquivo", "contrato.pdf", "application/pdf", new byte[]{1});
        assertThrows(ResponseStatusException.class, () -> service.enviarAoCliente(10L, 1L, arquivo));
        when(rbacService.temPermissao(1L, PermissaoCodigo.CONTRATO_EDITAR)).thenReturn(true);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        assertThrows(ResponseStatusException.class, () -> service.enviarAoCliente(10L, 1L, arquivo));
        verifyNoInteractions(arquivoStorageService, revisaoDocumentoService);
    }

    @Test
    void deveRecusarRevisaoManualEExigirArquivoEAprovacaoParaConcluir() {
        Contrato contrato = rascunho();
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(contrato));
        when(repository.findById(10L)).thenReturn(Optional.of(contrato));
        when(rbacService.temPermissao(1L, PermissaoCodigo.CONTRATO_EDITAR)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.moverPreparacao(10L, 1L, ContratoPreparacaoEtapa.REVISAO));
        assertThrows(ResponseStatusException.class, () -> service.moverPreparacao(10L, 1L, ContratoPreparacaoEtapa.CONCLUIDO));
        assertThrows(ResponseStatusException.class, () -> service.aprovar(10L, 1L, "APROVADO"));
        verify(repository, never()).save(any());
    }

    private Contrato rascunho() {
        Contrato contrato = new Contrato(); contrato.setIdContrato(10L);
        Empresa empresa = new Empresa(); empresa.setIdEmpresa(30L); empresa.setEmail("cliente@example.test");
        contrato.setEmpresa(empresa); contrato.setStatus("PENDENTE"); contrato.setEtapaPreparacao(ContratoPreparacaoEtapa.A_FAZER);
        return contrato;
    }
}
