package com.climb.api.service;
import com.climb.api.model.*;
import com.climb.api.repository.*;
import com.climb.api.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanilhaSecurityTest {
    PermissaoAcesso permissions; PlanilhaRepository repo; ContratoRepository contracts; PlanilhaService service; Planilha row;
    @BeforeEach void setup() {
        permissions=mock(PermissaoAcesso.class);repo=mock(PlanilhaRepository.class);contracts=mock(ContratoRepository.class);
        service=new PlanilhaService(repo,contracts,new PlanilhaAcesso(permissions));
        row=new Planilha();row.setIdPlanilha(1L);row.setBloqueada(false);
        var c=new Contrato(); c.setIdContrato(9L);row.setContrato(c);
        lenient().when(repo.findById(1L)).thenReturn(Optional.of(row));
        lenient().when(contracts.findById(9L)).thenReturn(Optional.of(c));
    }

    @Test void permissaoDeLeituraNaoPermiteAcessarOutroContrato() {
        when(permissions.tem("PLANILHA_LER")).thenReturn(true);
        assertThatThrownBy(() -> service.buscarPorId(1L)).isInstanceOf(AccessDeniedException.class);
        when(repo.findAll()).thenReturn(List.of(row));
        assertThat(service.listar()).isEmpty();
    }

    @Test void permissaoDeLeituraNaoPermiteEditarOuExcluir() {
        assertThatThrownBy(() -> service.atualizar(1L,row)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deletar(1L)).isInstanceOf(AccessDeniedException.class);
        verify(repo,never()).save(any());verify(repo,never()).delete(any());
    }

    @Test void editorNaoPodeAlterarPlanilhaBloqueada() {
        row.setBloqueada(true);
        when(permissions.tem("PLANILHA_EDITAR")).thenReturn(true);
        when(permissions.lerContrato(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.atualizar(1L,row)).isInstanceOf(AccessDeniedException.class);
        verify(repo,never()).save(any());
    }

    @Test void editorNaoPodeRemoverBloqueioOuRestricaoDeVisualizacao() {
        when(permissions.tem("PLANILHA_EDITAR")).thenReturn(true);
        when(permissions.lerContrato(9L)).thenReturn(true);
        var updated=new Planilha();updated.setContrato(row.getContrato());updated.setBloqueada(true);
        assertThatThrownBy(() -> service.atualizar(1L,updated)).isInstanceOf(AccessDeniedException.class);
        updated.setBloqueada(false);updated.setPermissaoVisualizacao("PERMITIR_ACESSO");
        assertThatThrownBy(() -> service.atualizar(1L,updated)).isInstanceOf(AccessDeniedException.class);
        verify(repo,never()).save(any());
    }

    @Test void restricaoDeVisualizacaoTambemEVerificada() {
        row.setPermissaoVisualizacao("PERMITIR_ACESSO");
        when(permissions.tem("PLANILHA_LER")).thenReturn(true);
        when(permissions.lerContrato(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.buscarPorId(1L)).isInstanceOf(AccessDeniedException.class);
        when(permissions.tem("PERMITIR_ACESSO")).thenReturn(true);
        assertThat(service.buscarPorId(1L)).isSameAs(row);
    }

    @Test void naoAceitaTrocaParaContratoSemAcesso() {
        when(permissions.tem("PLANILHA_EDITAR")).thenReturn(true);
        when(permissions.lerContrato(9L)).thenReturn(true);
        var another=new Contrato();another.setIdContrato(10L);when(contracts.findById(10L)).thenReturn(Optional.of(another));
        var updated=new Planilha();updated.setContrato(another);updated.setBloqueada(false);
        assertThatThrownBy(() -> service.atualizar(1L,updated)).isInstanceOf(AccessDeniedException.class);
        assertThat(row.getContrato().getIdContrato()).isEqualTo(9L);
        verify(repo,never()).save(any());
    }

    @Test void editorPodeEditarPlanilhaLiberadaSemReceberAcoesAdministrativas() {
        when(permissions.tem("PLANILHA_EDITAR")).thenReturn(true);
        when(permissions.lerContrato(9L)).thenReturn(true);
        var updated=new Planilha();updated.setContrato(row.getContrato());updated.setBloqueada(false);updated.setUrlGoogleSheets("https://docs.google.com/spreadsheets/teste");
        when(repo.save(row)).thenReturn(row);
        assertThat(service.atualizar(1L,updated).getUrlGoogleSheets()).endsWith("teste");
        var policy=new PlanilhaAcesso(permissions);
        assertThat(policy.tem("CRIAR")).isFalse();assertThat(policy.tem("DELETAR")).isFalse();assertThat(policy.tem("CONFIGURAR_ACESSO")).isFalse();
    }
}
