package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.Proposta;
import com.climb.api.model.RevisaoDocumento;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.dto.PropostaComercialRequestDTO;
import com.climb.api.model.dto.PropostaServicoDTO;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.model.enums.ServicoComercial;
import org.springframework.mock.web.MockMultipartFile;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.model.enums.RevisaoDocumentoStatus;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.HistoricoAprovacaoPropostaRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.RevisaoDocumentoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropostaServiceTest {
    @Mock private PropostaRepository repository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PipelineVendasNegocioRepository negocioRepository;
    @Mock private HistoricoAprovacaoPropostaRepository historicoRepository;
    @Mock private RevisaoDocumentoRepository revisaoDocumentoRepository;
    @Mock private RbacService rbacService;
    @Mock private CloudflareR2ArquivoStorageService storageService;
    @Mock private RevisaoDocumentoService revisaoDocumentoService;
    private PropostaService service;

    @BeforeEach
    void setUp() {
        service = new PropostaService(repository, empresaRepository, usuarioRepository, negocioRepository,
                historicoRepository, revisaoDocumentoRepository, rbacService, storageService, revisaoDocumentoService);
    }

    @Test
    void deveVincularPropostaAoNegocioDaMesmaEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1L);
        Usuario usuario = new Usuario();
        usuario.setId(2L);
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setFunil(new com.climb.api.model.PipelineVendasFunil());
        negocio.setIdNegocio(3L);
        negocio.setEmpresa(empresa);
        when(rbacService.temPermissao(2L, PermissaoCodigo.PROPOSTA_CRUD)).thenReturn(true);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(negocioRepository.findById(3L)).thenReturn(Optional.of(negocio));
        when(repository.save(any())).thenAnswer(invocation -> {
            Proposta proposta = invocation.getArgument(0);
            proposta.setIdProposta(4L);
            return proposta;
        });
        PropostaRequestDTO request = new PropostaRequestDTO(
                1L, 3L, 2L, "propostas/teste.pdf", new BigDecimal("1000.00"),
                PropostaStatus.PENDENTE, LocalDate.now());

        var response = service.criar(request);

        assertEquals(3L, response.negocioId());
    }

    @Test
    void deveExporStatusDaRevisaoSolicitadaPeloCliente() {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1L);
        Usuario usuario = new Usuario();
        usuario.setId(2L);
        Proposta proposta = new Proposta();
        proposta.setIdProposta(4L);
        proposta.setEmpresa(empresa);
        proposta.setUsuario(usuario);
        proposta.setStatus(PropostaStatus.PENDENTE);
        RevisaoDocumento revisao = new RevisaoDocumento();
        revisao.setTipo(RevisaoDocumentoTipo.PROPOSTA);
        revisao.setReferenciaId(4L);
        revisao.setStatus(RevisaoDocumentoStatus.AJUSTES_SOLICITADOS);
        when(repository.findByEmpresaIdEmpresaOrderByDataCriacaoDescIdPropostaDesc(1L))
                .thenReturn(List.of(proposta));
        when(revisaoDocumentoRepository.findByTipoAndReferenciaIdIn(
                RevisaoDocumentoTipo.PROPOSTA, List.of(4L))).thenReturn(List.of(revisao));

        var response = service.listar(1L, null);

        assertEquals(RevisaoDocumentoStatus.AJUSTES_SOLICITADOS, response.getFirst().revisaoStatus());
    }

    @Test
    void deveSalvarERetornarValoresComissoesPorServicoERecebimentos() {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1L);
        Usuario usuario = new Usuario();
        usuario.setId(2L);
        when(rbacService.temPermissao(2L, PermissaoCodigo.PROPOSTA_CRUD)).thenReturn(true);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(storageService.salvar(any(), anyString())).thenReturn(new ArquivoUploadResponseDTO(
                "proposta.pdf", "application/pdf", 4, "propostas/proposta.pdf", "propostas/proposta.pdf"));
        when(repository.save(any())).thenAnswer(invocation -> {
            Proposta proposta = invocation.getArgument(0);
            proposta.setIdProposta(4L);
            return proposta;
        });
        var servicos = List.of(
                new PropostaServicoDTO(ServicoComercial.BPO, new BigDecimal("30000.00"), new BigDecimal("25.00"), null),
                new PropostaServicoDTO(ServicoComercial.CFO, new BigDecimal("20000.00"), null, new BigDecimal("10.00")));
        var config = new PropostaComercialRequestDTO(ServicoComercial.BPO, LocalDate.of(2026, 10, 1), 12, 12,
                false, null, null, List.of(), List.of(), List.of(), null, servicos, PropostaComercialValidatorTest.recebimentosPorServico());
        var arquivo = new MockMultipartFile("arquivo", "proposta.pdf", "application/pdf", new byte[]{1, 2});

        var response = service.criarComArquivo(1L, null, 2L, arquivo, new BigDecimal("50000.00"), config);

        assertEquals(2, response.servicos().size());
        assertEquals(new BigDecimal("30000.00"), response.servicos().getFirst().valor());
        assertEquals(new BigDecimal("25.00"), response.servicos().getFirst().comissaoTecnicoPercentual());
        assertEquals(new BigDecimal("20.00"), response.servicos().getFirst().comissaoComercialPercentual());
        assertEquals(new BigDecimal("30.00"), response.servicos().get(1).comissaoTecnicoPercentual());
        assertEquals(new BigDecimal("10.00"), response.servicos().get(1).comissaoComercialPercentual());
        assertEquals(12, response.quantidadeParcelas());
        assertEquals(PropostaComercialValidatorTest.recebimentosPorServico(), response.recebimentos());
        org.mockito.ArgumentCaptor<Proposta> persisted = org.mockito.ArgumentCaptor.forClass(Proposta.class);
        org.mockito.Mockito.verify(repository).save(persisted.capture());
        assertEquals(24, persisted.getValue().getRecebimentosPorServico().size());
        when(repository.findById(4L)).thenReturn(Optional.of(persisted.getValue()));
        assertEquals(response.recebimentos(), service.buscarPorId(4L).recebimentos());
    }
}
