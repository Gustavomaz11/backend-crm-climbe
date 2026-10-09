package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TarefaColaboracaoServiceTest {
    @Mock ContratoKanbanService contratos;
    @Mock PipelineTarefaService comercial;
    @Mock TarefaComentarioRepository comentarios;
    @Mock TarefaAnexoRepository anexos;
    @Mock UsuarioRepository usuarios;
    @Mock CloudflareR2ArquivoStorageService storage;
    @Mock ArquivoValidationService validation;
    @InjectMocks TarefaColaboracaoService service;
    Usuario autor;
    ContratoKanbanTask contrato;
    PipelineVendasTarefa pipeline;
    MockMultipartFile file = new MockMultipartFile("arquivos", "balanco.pdf", "application/pdf", new byte[]{1});

    @BeforeEach void setup() {
        autor = new Usuario(); autor.setId(2L); autor.setNomeCompleto("Ana");
        contrato = new ContratoKanbanTask(); contrato.setIdTask(10L);
        pipeline = new PipelineVendasTarefa(); pipeline.setIdTarefa(10L);
    }
    void acesso(TarefaTipo tipo) {
        if (tipo == TarefaTipo.CONTRATO) when(contratos.exigirTaskVisivel(10L, 2L)).thenReturn(contrato);
        else when(comercial.exigirTarefaVisivel(10L, 2L)).thenReturn(pipeline);
    }
    TarefaComentario comentario(TarefaTipo tipo, Long id, Long taskId) {
        TarefaComentario item = new TarefaComentario(); item.setId(id); item.setAutor(autor); item.setConteudo("Conferido");
        if (tipo == TarefaTipo.CONTRATO) { var task = new ContratoKanbanTask(); task.setIdTask(taskId); item.setContratoTask(task); }
        else { var task = new PipelineVendasTarefa(); task.setIdTarefa(taskId); item.setPipelineTarefa(task); }
        return item;
    }

    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void deveSalvarRespostaComAnexoEPreservarAutor(TarefaTipo tipo) {
        acesso(tipo);
        when(usuarios.findById(2L)).thenReturn(Optional.of(autor));
        when(comentarios.findById(30L)).thenReturn(Optional.of(comentario(tipo, 30L, 10L)));
        when(comentarios.save(any())).thenAnswer(invocation -> { TarefaComentario item = invocation.getArgument(0); item.setId(31L); return item; });
        when(storage.salvar(eq(file), anyString())).thenReturn(new ArquivoUploadResponseDTO("balanco.pdf", "application/pdf", 1, "privado/balanco", "privado/balanco"));
        when(anexos.save(any())).thenAnswer(invocation -> { TarefaAnexo item = invocation.getArgument(0); item.setId(40L); return item; });
        var resposta = service.comentar(tipo, 10L, 2L, "  Está correto  ", 30L, List.of(file));
        assertEquals(30L, resposta.comentarioPaiId()); assertEquals("Está correto", resposta.conteudo());
        assertEquals(2L, resposta.autor().id()); assertEquals("balanco.pdf", resposta.anexos().getFirst().nome());
        ArgumentCaptor<TarefaAnexo> captor = ArgumentCaptor.forClass(TarefaAnexo.class);
        verify(anexos).save(captor.capture()); assertEquals(31L, captor.getValue().getComentario().getId());
        verify(validation).validar(file);
    }

    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void deveRecusarRespostaAComentarioDeOutraTarefa(TarefaTipo tipo) {
        acesso(tipo);
        when(comentarios.findById(30L)).thenReturn(Optional.of(comentario(tipo, 30L, 99L)));
        var erro = assertThrows(ResponseStatusException.class, () -> service.comentar(tipo, 10L, 2L, "Resposta", 30L, List.of(file)));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verifyNoInteractions(storage, anexos, validation);
    }

    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void usuarioSemAcessoNaoPodeListarAnexarComentarOuBaixar(TarefaTipo tipo) {
        var forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (tipo == TarefaTipo.CONTRATO) when(contratos.exigirTaskVisivel(10L, 2L)).thenThrow(forbidden);
        else when(comercial.exigirTarefaVisivel(10L, 2L)).thenThrow(forbidden);
        assertThrows(ResponseStatusException.class, () -> service.listar(tipo, 10L, 2L));
        assertThrows(ResponseStatusException.class, () -> service.anexar(tipo, 10L, 2L, List.of(file)));
        assertThrows(ResponseStatusException.class, () -> service.comentar(tipo, 10L, 2L, "Mensagem", null, List.of(file)));
        assertThrows(ResponseStatusException.class, () -> service.baixar(tipo, 10L, 40L, 2L));
        verifyNoInteractions(comentarios, anexos, storage, validation, usuarios);
    }

    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void deveValidarTarefaDoAnexoAntesDeBaixar(TarefaTipo tipo) {
        acesso(tipo);
        var arquivo = new TarefaAnexo(); arquivo.setId(40L);
        var outra = comentario(tipo, 30L, 99L); arquivo.setContratoTask(outra.getContratoTask()); arquivo.setPipelineTarefa(outra.getPipelineTarefa());
        when(anexos.findById(40L)).thenReturn(Optional.of(arquivo));
        assertThrows(ResponseStatusException.class, () -> service.baixar(tipo, 10L, 40L, 2L));
        verifyNoInteractions(storage);
    }

    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void deveSepararArquivosDaTarefaDosComentariosEPermitirBaixar(TarefaTipo tipo) {
        acesso(tipo);
        var comentario = comentario(tipo, 30L, 10L);
        var geral = new TarefaAnexo(); geral.setId(40L); geral.setAutor(autor); geral.setNome("balanco.pdf"); geral.setContentType("application/pdf"); geral.setChave("privado/balanco");
        geral.setContratoTask(comentario.getContratoTask()); geral.setPipelineTarefa(comentario.getPipelineTarefa());
        var resposta = new TarefaAnexo(); resposta.setId(41L); resposta.setAutor(autor); resposta.setComentario(comentario);
        if (tipo == TarefaTipo.CONTRATO) {
            when(anexos.findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(10L)).thenReturn(List.of(geral, resposta));
            when(comentarios.findByContratoTask_IdTaskOrderByCriadoEmAscIdAsc(10L)).thenReturn(List.of(comentario));
        } else {
            when(anexos.findByPipelineTarefa_IdTarefaOrderByCriadoEmAscIdAsc(10L)).thenReturn(List.of(geral, resposta));
            when(comentarios.findByPipelineTarefa_IdTarefaOrderByCriadoEmAscIdAsc(10L)).thenReturn(List.of(comentario));
        }
        var resultado = service.listar(tipo, 10L, 2L);
        assertEquals(1, resultado.anexos().size()); assertEquals(41L, resultado.comentarios().getFirst().anexos().getFirst().id());
        when(anexos.findById(40L)).thenReturn(Optional.of(geral)); when(storage.baixar("privado/balanco")).thenReturn(new byte[]{1, 2});
        assertArrayEquals(new byte[]{1, 2}, service.baixar(tipo, 10L, 40L, 2L).conteudo());
    }
}
