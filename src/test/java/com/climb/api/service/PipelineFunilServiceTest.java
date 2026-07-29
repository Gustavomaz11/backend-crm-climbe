package com.climb.api.service;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.PipelineVendasEtapa;
import com.climb.api.model.PipelineVendasFunil;
import com.climb.api.model.dto.PipelineEtapaConfiguracaoRequestDTO;
import com.climb.api.model.dto.PipelineFunilRequestDTO;
import com.climb.api.model.enums.PipelineVendasResultado;
import com.climb.api.repository.PipelineVendasFunilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineFunilServiceTest {

    @Mock private PipelineVendasFunilRepository repository;
    @Mock private RbacService rbacService;

    private PipelineFunilService service;

    @BeforeEach
    void setUp() {
        service = new PipelineFunilService(
                repository, rbacService, new PipelineFunilValidator(), new PipelineFunilMapper());
    }

    @Test
    void deveCriarFunilCompletoComEtapasOrdenadas() {
        when(rbacService.temPermissao(7L, PermissaoCodigo.COMERCIAL_FUNIL_CRIAR)).thenReturn(true);
        when(repository.findTopByOrderByPosicaoDesc()).thenReturn(Optional.empty());
        when(repository.save(any(PipelineVendasFunil.class))).thenAnswer(invocation -> {
            PipelineVendasFunil funil = invocation.getArgument(0);
            funil.setIdFunil(20L);
            return funil;
        });

        var response = service.criar(7L, requestPadrao());

        assertEquals(20L, response.id());
        assertEquals("Venda consultiva", response.estrategia());
        assertEquals(1, response.posicao());
        assertEquals(3, response.etapas().size());
        assertEquals(1, response.etapas().get(0).posicao());
        assertEquals(List.of("nomeContato", "telefone"), response.etapas().get(0).camposObrigatorios());
        assertTrue(response.etapas().get(1).sucesso());
        assertTrue(response.etapas().get(2).perda());
    }

    @Test
    void deveRecusarEtapaQueRepresentaSucessoEPerda() {
        when(rbacService.temPermissao(7L, PermissaoCodigo.COMERCIAL_FUNIL_CRIAR)).thenReturn(true);
        PipelineEtapaConfiguracaoRequestDTO invalida = etapa(null, "Final", true, true, true);
        PipelineFunilRequestDTO request = new PipelineFunilRequestDTO(
                "Funil inválido", null, "Teste", true, List.of(invalida));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar(7L, request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void deveDuplicarFunilComoInativoPreservandoConfiguracao() {
        PipelineVendasFunil original = funil(10L, 2, "Assessoria");
        original.getEtapas().add(etapaPersistida(original, 100L, "Diagnóstico", PipelineVendasResultado.ABERTO));
        when(rbacService.temPermissao(7L, PermissaoCodigo.COMERCIAL_FUNIL_DUPLICAR)).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(original));
        when(repository.findTopByOrderByPosicaoDesc()).thenReturn(Optional.of(original));
        when(repository.save(any(PipelineVendasFunil.class))).thenAnswer(invocation -> {
            PipelineVendasFunil copia = invocation.getArgument(0);
            copia.setIdFunil(11L);
            return copia;
        });

        var response = service.duplicar(10L, 7L);

        assertEquals(11L, response.id());
        assertFalse(response.ativo());
        assertEquals(3, response.posicao());
        assertEquals("Diagnóstico", response.etapas().get(0).nome());
        assertEquals(List.of("nomeContato"), response.etapas().get(0).camposObrigatorios());
    }

    @Test
    void deveReordenarTodosOsFunis() {
        PipelineVendasFunil primeiro = funil(1L, 1, "Primeiro");
        PipelineVendasFunil segundo = funil(2L, 2, "Segundo");
        when(rbacService.temPermissao(7L, PermissaoCodigo.COMERCIAL_FUNIL_ORDENAR)).thenReturn(true);
        when(repository.findAllByOrderByPosicaoAsc()).thenReturn(new ArrayList<>(List.of(primeiro, segundo)));

        var response = service.reordenar(7L, List.of(2L, 1L));

        assertEquals(List.of(2L, 1L), response.stream().map(item -> item.id()).toList());
        assertEquals(1, segundo.getPosicao());
        assertEquals(2, primeiro.getPosicao());
        verify(repository).saveAll(anyList());
    }

    private PipelineFunilRequestDTO requestPadrao() {
        return new PipelineFunilRequestDTO(
                "Consultoria patrimonial",
                "Processo para novos clientes",
                "Venda consultiva",
                true,
                List.of(
                        new PipelineEtapaConfiguracaoRequestDTO(
                                null, "Diagnóstico", "Mapear cenário", "Diagnóstico registrado", 3,
                                false, false, List.of("nomeContato", "telefone"), true),
                        etapa(null, "Fechado", true, false, true),
                        etapa(null, "Perdido", false, true, true)
                )
        );
    }

    private PipelineEtapaConfiguracaoRequestDTO etapa(Long id, String nome, boolean sucesso, boolean perda, boolean ativo) {
        return new PipelineEtapaConfiguracaoRequestDTO(
                id, nome, null, null, 5, sucesso, perda, List.of(), ativo);
    }

    private PipelineVendasFunil funil(Long id, int posicao, String nome) {
        PipelineVendasFunil funil = new PipelineVendasFunil();
        funil.setIdFunil(id);
        funil.setCodigo(nome.toUpperCase());
        funil.setNome(nome);
        funil.setDescricao("Descrição");
        funil.setEstrategia("Estratégia");
        funil.setPosicao(posicao);
        funil.setAtivo(true);
        return funil;
    }

    private PipelineVendasEtapa etapaPersistida(PipelineVendasFunil funil, Long id, String nome,
                                                 PipelineVendasResultado resultado) {
        PipelineVendasEtapa etapa = new PipelineVendasEtapa();
        etapa.setIdEtapa(id);
        etapa.setFunil(funil);
        etapa.setCodigo(nome.toUpperCase());
        etapa.setNome(nome);
        etapa.setObjetivo("Objetivo");
        etapa.setCriteriosConclusao("Critério");
        etapa.setTempoMaximoPermanenciaDias(4);
        etapa.setCamposObrigatorios(new ArrayList<>(List.of("nomeContato")));
        etapa.setResultado(resultado);
        etapa.setPosicao(1);
        etapa.setAtivo(true);
        return etapa;
    }
}
