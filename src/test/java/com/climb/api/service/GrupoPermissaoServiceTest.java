package com.climb.api.service;

import com.climb.api.model.GrupoPermissao;
import com.climb.api.model.Permissao;
import com.climb.api.model.dto.GrupoPermissaoRequestDTO;
import com.climb.api.repository.GrupoPermissaoRepository;
import com.climb.api.repository.PermissaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrupoPermissaoServiceTest {

    @Mock private GrupoPermissaoRepository repository;
    @Mock private PermissaoRepository permissaoRepository;

    @Test
    void deveCriarGrupoComAsPermissoesSelecionadas() {
        Permissao permissao = new Permissao();
        permissao.setIdPermissao(3L);
        permissao.setCodigo("CONTRATO_CRUD");
        when(permissaoRepository.findAllById(Set.of(3L))).thenReturn(List.of(permissao));
        when(repository.save(any(GrupoPermissao.class))).thenAnswer(invocation -> {
            GrupoPermissao grupo = invocation.getArgument(0);
            grupo.setId(9L);
            return grupo;
        });

        GrupoPermissaoService service = new GrupoPermissaoService(repository, permissaoRepository);
        var resposta = service.criar(new GrupoPermissaoRequestDTO(
                "Comercial", "Equipe comercial", Set.of(3L)));

        assertEquals(9L, resposta.id());
        assertEquals("Comercial", resposta.nome());
        assertEquals(1, resposta.permissoes().size());
    }

    @Test
    void deveEditarNomeDescricaoEPermissoesDoGrupo() {
        Permissao permissaoAtual = new Permissao();
        permissaoAtual.setIdPermissao(3L);
        permissaoAtual.setCodigo("CONTRATO_CRUD");
        Permissao novaPermissao = new Permissao();
        novaPermissao.setIdPermissao(4L);
        novaPermissao.setCodigo("PROPOSTA_CRUD");

        GrupoPermissao grupo = new GrupoPermissao();
        grupo.setId(9L);
        grupo.setNome("Comercial");
        grupo.setDescricao("Descricao antiga");
        grupo.getPermissoes().add(permissaoAtual);

        when(repository.findById(9L)).thenReturn(Optional.of(grupo));
        when(permissaoRepository.findAllById(Set.of(4L))).thenReturn(List.of(novaPermissao));
        when(repository.save(grupo)).thenReturn(grupo);

        GrupoPermissaoService service = new GrupoPermissaoService(repository, permissaoRepository);
        var resposta = service.atualizar(9L, new GrupoPermissaoRequestDTO(
                "Administrativo", "Descricao nova", Set.of(4L)));

        assertEquals("Administrativo", resposta.nome());
        assertEquals("Descricao nova", resposta.descricao());
        assertEquals(Set.of(novaPermissao), resposta.permissoes());
    }
}
