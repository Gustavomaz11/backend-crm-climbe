package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.Proposta;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.HistoricoAprovacaoPropostaRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.PropostaRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropostaServiceTest {
    @Mock private PropostaRepository repository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PipelineVendasNegocioRepository negocioRepository;
    @Mock private HistoricoAprovacaoPropostaRepository historicoRepository;
    @Mock private RbacService rbacService;
    @Mock private CloudflareR2ArquivoStorageService storageService;
    @Mock private RevisaoDocumentoService revisaoDocumentoService;
    private PropostaService service;

    @BeforeEach
    void setUp() {
        service = new PropostaService(repository, empresaRepository, usuarioRepository, negocioRepository,
                historicoRepository, rbacService, storageService, revisaoDocumentoService);
    }

    @Test
    void deveVincularPropostaAoNegocioDaMesmaEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1L);
        Usuario usuario = new Usuario();
        usuario.setId(2L);
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
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
}
