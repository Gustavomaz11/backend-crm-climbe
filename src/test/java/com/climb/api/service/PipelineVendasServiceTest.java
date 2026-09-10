package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.model.dto.PipelineNegocioResponseDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.PipelineVendasEtapaRepository;
import com.climb.api.repository.PipelineVendasFunilRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.RevisaoDocumentoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineVendasServiceTest {

    @Mock private PipelineVendasEtapaRepository etapaRepository;
    @Mock private PipelineVendasFunilRepository funilRepository;
    @Mock private PipelineVendasNegocioRepository negocioRepository;
    @Mock private PropostaRepository propostaRepository;
    @Mock private RevisaoDocumentoRepository revisaoDocumentoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private PipelineEmpresaCadastroService empresaCadastroService;
    @Mock private ContratoService contratoService;
    @Mock private PipelineHistoricoService historicoService;
    @Mock private PipelineMotivoPerdaService motivoPerdaService;
    @Mock private PipelineMovimentacaoEtapaService movimentacaoEtapaService;
    @Mock private RbacService rbacService;

    private PipelineVendasService service;

    @BeforeEach
    void setUp() {
        service = new PipelineVendasService(
                etapaRepository,
                funilRepository,
                negocioRepository,
                propostaRepository,
                revisaoDocumentoRepository,
                usuarioRepository,
                empresaRepository,
                empresaCadastroService,
                contratoService,
                historicoService,
                motivoPerdaService,
                movimentacaoEtapaService,
                rbacService, mock(PipelinePreVendaService.class), mock(PipelineCadastroService.class)
        );
    }

    @Test
    void deveCriarNegocioNaPrimeiraEtapaDoFunil() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasEtapa etapaInicial = etapa(10L, "REUNIAO_MARCADA", PipelineVendasResultado.ABERTO);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CRIAR)).thenReturn(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(funilRepository.findByAtivoTrueOrderByPosicaoAsc()).thenReturn(List.of(etapaInicial.getFunil()));
        when(etapaRepository.findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(1L)).thenReturn(List.of(etapaInicial));
        when(negocioRepository.save(any())).thenAnswer(invocation -> {
            PipelineVendasNegocio negocio = invocation.getArgument(0);
            negocio.setIdNegocio(100L);
            return negocio;
        });

        service.criar(1L, request(1L));

        ArgumentCaptor<PipelineVendasNegocio> captor = ArgumentCaptor.forClass(PipelineVendasNegocio.class);
        verify(negocioRepository).save(captor.capture());
        assertEquals(etapaInicial, captor.getValue().getEtapa());
        assertEquals(PipelineVendasResultado.ABERTO, captor.getValue().getResultado());
        assertNotNull(captor.getValue().getCriadoEm());
        assertNotNull(captor.getValue().getUltimaMovimentacaoEm());
        verify(movimentacaoEtapaService).iniciar(eq(captor.getValue()), any(LocalDateTime.class));
    }

    @Test
    void deveCadastrarEmpresaEManterMultiplosServicosAoCriarNegocio() {
        Usuario usuario = usuario(1L, "Gestor");
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(30L);
        PipelineVendasEtapa etapaInicial = etapa(10L, "REUNIAO_MARCADA", PipelineVendasResultado.ABERTO);
        PipelineNegocioRequestDTO request = new PipelineNegocioRequestDTO(
                null, null, "Apex Ventures", "Maria Silva", "11999999999", "maria@apex.com",
                1L, null, LocalDateTime.now().plusDays(1), "Indicação", "Ativa", "BPO",
                new BigDecimal("150000.00"), null, List.of("BPO", "CFO"), true,
                "12.345.678/0001-95"
        );
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CRIAR)).thenReturn(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(empresaCadastroService.cadastrar(request)).thenReturn(empresa);
        when(funilRepository.findByAtivoTrueOrderByPosicaoAsc()).thenReturn(List.of(etapaInicial.getFunil()));
        when(etapaRepository.findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(1L)).thenReturn(List.of(etapaInicial));
        when(negocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineNegocioResponseDTO response = service.criar(1L, request);

        assertEquals(30L, response.empresaId());
        assertEquals(List.of("BPO", "CFO"), response.servicosInteresse());
        verify(empresaCadastroService).cadastrar(request);
    }

    @Test
    void devePermitirCriarNegocioSemServicoDeInteresse() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasEtapa etapaInicial = etapa(10L, "REUNIAO_MARCADA", PipelineVendasResultado.ABERTO);
        etapaInicial.setCamposObrigatorios(List.of("servicoInteresse"));
        PipelineNegocioRequestDTO request = new PipelineNegocioRequestDTO(
                null, null, "Apex Ventures", "Maria Silva", "11999999999", "maria@apex.com",
                1L, null, null, "Indicação", "Ativa", "",
                null, null, List.of(), false, null
        );
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CRIAR)).thenReturn(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(funilRepository.findByAtivoTrueOrderByPosicaoAsc()).thenReturn(List.of(etapaInicial.getFunil()));
        when(etapaRepository.findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(1L)).thenReturn(List.of(etapaInicial));
        when(negocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PipelineNegocioResponseDTO response = service.criar(1L, request);

        assertTrue(response.servicosInteresse().isEmpty());
        assertEquals("", response.servicoInteresse());
    }

    @Test
    void deveExigirPropostaAntesDeMoverParaPropostaApresentada() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasNegocio negocio = negocio(100L, usuario,
                etapa(10L, "PROPOSTA_EM_ELABORACAO", PipelineVendasResultado.ABERTO));
        PipelineVendasEtapa apresentada = etapa(20L, "PROPOSTA_APRESENTADA", PipelineVendasResultado.ABERTO);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_MOVIMENTAR)).thenReturn(true);
        when(negocioRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(negocio));
        when(etapaRepository.findById(20L)).thenReturn(Optional.of(apresentada));
        when(propostaRepository.existsByNegocioIdNegocio(100L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.mover(100L, 1L, 20L, null, null));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("Crie uma proposta"));
        verify(negocioRepository, never()).save(any());
    }

    @Test
    void devePermitirMoverParaPropostaApresentadaQuandoExisteProposta() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasNegocio negocio = negocio(100L, usuario,
                etapa(10L, "PROPOSTA_EM_ELABORACAO", PipelineVendasResultado.ABERTO));
        PipelineVendasEtapa apresentada = etapa(20L, "PROPOSTA_APRESENTADA", PipelineVendasResultado.ABERTO);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_MOVIMENTAR)).thenReturn(true);
        when(negocioRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(negocio));
        when(etapaRepository.findById(20L)).thenReturn(Optional.of(apresentada));
        when(propostaRepository.existsByNegocioIdNegocio(100L)).thenReturn(true);
        when(negocioRepository.save(negocio)).thenReturn(negocio);

        service.mover(100L, 1L, 20L, null, null);

        assertEquals(apresentada, negocio.getEtapa());
    }

    @Test
    void deveAtualizarResultadoAoMoverParaFechado() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasNegocio negocio = negocio(100L, usuario, etapa(10L, "NEGOCIACAO", PipelineVendasResultado.ABERTO));
        PipelineVendasEtapa fechado = etapa(20L, "FECHADO", PipelineVendasResultado.GANHO);
        LocalDateTime movimentacaoAnterior = negocio.getUltimaMovimentacaoEm();
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_MOVIMENTAR)).thenReturn(true);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CONCLUIR)).thenReturn(true);
        when(negocioRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(negocio));
        when(etapaRepository.findById(20L)).thenReturn(Optional.of(fechado));
        when(negocioRepository.save(negocio)).thenReturn(negocio);

        service.mover(100L, 1L, 20L, null, null);

        assertEquals(fechado, negocio.getEtapa());
        assertEquals(PipelineVendasResultado.GANHO, negocio.getResultado());
        assertTrue(negocio.getUltimaMovimentacaoEm().isAfter(movimentacaoAnterior));
    }

    @Test
    void deveConverterNegocioGanhoEmContrato() {
        Usuario usuario = usuario(1L, "Gestor");
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(30L);
        empresa.setNomeFantasia("Apex Ventures");
        PipelineVendasNegocio negocio = negocio(100L, usuario, etapa(20L, "FECHADO", PipelineVendasResultado.GANHO));
        negocio.setResultado(PipelineVendasResultado.GANHO);
        Contrato contrato = new Contrato();
        contrato.setIdContrato(50L);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CONVERTER_CONTRATO)).thenReturn(true);
        when(negocioRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(negocio));
        when(empresaRepository.findById(30L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(contratoService.criarAPartirDoPipeline(empresa, usuario, usuario)).thenReturn(contrato);
        when(negocioRepository.save(negocio)).thenReturn(negocio);

        var response = service.converterEmContrato(100L, 1L, 30L);

        assertEquals(50L, response.contratoId());
        assertEquals(empresa, negocio.getEmpresa());
        verify(contratoService).criarAPartirDoPipeline(empresa, usuario, usuario);
    }

    @Test
    void deveBloquearCriacaoSemPermissaoComercialGranular() {
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CRIAR)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar(1L, request(1L))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(negocioRepository, never()).save(any());
    }

    @Test
    void deveExigirCamposConfiguradosAntesDeMoverParaEtapa() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasNegocio negocio = negocio(100L, usuario, etapa(10L, "DIAGNOSTICO", PipelineVendasResultado.ABERTO));
        negocio.setEmail(null);
        PipelineVendasEtapa proposta = etapa(20L, "PROPOSTA", PipelineVendasResultado.ABERTO);
        proposta.setCamposObrigatorios(List.of("email", "valorEstimadoProposta"));
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_MOVIMENTAR)).thenReturn(true);
        when(negocioRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(negocio));
        when(etapaRepository.findById(20L)).thenReturn(Optional.of(proposta));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.mover(100L, 1L, 20L, null, null)
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("email"));
        verify(negocioRepository, never()).save(any());
    }

    @Test
    void deveExigirMotivoAoMarcarNegocioComoPerdido() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasNegocio negocio = negocio(100L, usuario, etapa(10L, "NEGOCIACAO", PipelineVendasResultado.ABERTO));
        PipelineVendasEtapa perdido = etapa(30L, "PERDIDO", PipelineVendasResultado.PERDIDO);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_CONCLUIR)).thenReturn(true);
        when(negocioRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(negocio));
        when(etapaRepository.findFirstByFunilIdFunilAndResultadoAndAtivoTrueOrderByPosicaoAsc(
                1L, PipelineVendasResultado.PERDIDO)).thenReturn(Optional.of(perdido));
        when(motivoPerdaService.buscarAtivoObrigatorio(null))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "O motivo da perda é obrigatório"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.marcarResultado(100L, 1L, PipelineVendasResultado.PERDIDO, null, null));

        assertEquals(400, exception.getStatusCode().value());
        verify(negocioRepository, never()).save(any());
    }

    @Test
    void deveSinalizarNoBoardQuandoClienteSolicitouAjustesNaProposta() {
        Usuario usuario = usuario(1L, "Gestor");
        PipelineVendasEtapa etapa = etapa(10L, "PROPOSTA_APRESENTADA", PipelineVendasResultado.ABERTO);
        PipelineVendasNegocio negocio = negocio(100L, usuario, etapa);
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL)).thenReturn(true);
        when(funilRepository.findByAtivoTrueOrderByPosicaoAsc()).thenReturn(List.of(etapa.getFunil()));
        when(etapaRepository.findByFunilIdFunilAndAtivoTrueOrderByPosicaoAsc(1L)).thenReturn(List.of(etapa));
        when(negocioRepository.findByFunilIdFunilOrderByEtapaPosicaoAscUltimaMovimentacaoEmDesc(1L))
                .thenReturn(List.of(negocio));
        when(propostaRepository.findNegocioIdsComProposta(List.of(100L))).thenReturn(List.of(100L));
        when(revisaoDocumentoRepository.findNegocioIdsComAjustesSolicitados(List.of(100L)))
                .thenReturn(List.of(100L));

        PipelineNegocioResponseDTO response = service.buscarBoard(1L, null).etapas().getFirst().negocios().getFirst();

        assertTrue(response.possuiProposta());
        assertTrue(response.propostaAjustesPendentes());
    }

    private PipelineNegocioRequestDTO request(Long responsavelId) {
        return new PipelineNegocioRequestDTO(
                null,
                null,
                "Apex Ventures",
                "Maria Silva",
                "11999999999",
                "maria@apex.com",
                responsavelId,
                null,
                LocalDateTime.now().plusDays(1),
                "Indicação",
                "Diagnóstico consultivo",
                "M&A",
                new BigDecimal("150000.00"),
                "Primeiro contato"
        );
    }

    private Usuario usuario(Long id, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
        return usuario;
    }

    private PipelineVendasEtapa etapa(Long id, String codigo, PipelineVendasResultado resultado) {
        PipelineVendasEtapa etapa = new PipelineVendasEtapa();
        etapa.setIdEtapa(id);
        PipelineVendasFunil funil = new PipelineVendasFunil();
        funil.setIdFunil(1L);
        funil.setNome("Funil padrão");
        funil.setAtivo(true);
        etapa.setFunil(funil);
        etapa.setCodigo(codigo);
        etapa.setNome(codigo);
        etapa.setPosicao(id.intValue());
        etapa.setResultado(resultado);
        etapa.setAtivo(true);
        return etapa;
    }

    private PipelineVendasNegocio negocio(Long id, Usuario responsavel, PipelineVendasEtapa etapa) {
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(id);
        negocio.setFunil(etapa.getFunil());
        negocio.setNomeEmpresa("Apex Ventures");
        negocio.setNomeContato("Maria Silva");
        negocio.setTelefone("11999999999");
        negocio.setEmail("maria@apex.com");
        negocio.setResponsavel(responsavel);
        negocio.setEtapa(etapa);
        negocio.setOrigemNegocio("Indicação");
        negocio.setEstrategiaComercial("Diagnóstico");
        negocio.setServicoInteresse("M&A");
        negocio.setResultado(etapa.getResultado());
        negocio.setCriadoPor(responsavel);
        negocio.setCriadoEm(LocalDateTime.now().minusDays(1));
        negocio.setUltimaMovimentacaoEm(LocalDateTime.now().minusMinutes(1));
        return negocio;
    }
}
