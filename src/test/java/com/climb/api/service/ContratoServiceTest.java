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
import static org.mockito.Mockito.when;

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

    private ContratoService service;

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
}
