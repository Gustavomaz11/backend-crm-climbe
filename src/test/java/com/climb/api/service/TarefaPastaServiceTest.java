package com.climb.api.service;

import com.climb.api.model.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.repository.TarefaPastaRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class TarefaPastaServiceTest {
    @Mock TarefaPastaRepository repository;
    @InjectMocks TarefaPastaService service;
    private TarefaPasta pasta(TarefaTipo tipo, Long id, Long tarefaId) {
        var pasta = new TarefaPasta(); pasta.setId(id); pasta.setNome("Documentos");
        if (tipo == TarefaTipo.CONTRATO) { var task = new ContratoKanbanTask(); task.setIdTask(tarefaId); pasta.setContratoTask(task); }
        else { var task = new PipelineVendasTarefa(); task.setIdTarefa(tarefaId); pasta.setPipelineTarefa(task); }
        return pasta;
    }
    private void listar(TarefaTipo tipo, List<TarefaPasta> pastas) {
        if (tipo == TarefaTipo.CONTRATO) when(repository.findByContratoTask_IdTaskOrderByNomeAscIdAsc(10L)).thenReturn(pastas);
        else when(repository.findByPipelineTarefa_IdTarefaOrderByNomeAscIdAsc(10L)).thenReturn(pastas);
    }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void criaPastaESubpastaComMesmoNomeEmNiveisDiferentes(TarefaTipo tipo) {
        var task = pasta(tipo, 50L, 10L); var autor = new Usuario(); autor.setId(2L);
        var existentes = new ArrayList<TarefaPasta>(); listar(tipo, existentes);
        when(repository.save(any())).thenAnswer(invocation -> { TarefaPasta item = invocation.getArgument(0); item.setId(50L + existentes.size()); existentes.add(item); return item; });
        var raiz = service.criar(task.getContratoTask(), task.getPipelineTarefa(), autor, " Documentos ", null);
        when(repository.findById(50L)).thenReturn(Optional.of(raiz));
        var filha = service.criar(task.getContratoTask(), task.getPipelineTarefa(), autor, "Documentos", 50L);
        assertEquals("Documentos", raiz.getNome()); assertSame(raiz, filha.getPastaPai()); assertSame(autor, filha.getAutor());
        assertEquals(50L, service.toResponse(filha).pastaPaiId());
    }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void rejeitaNomeDuplicadoNoMesmoNivel(TarefaTipo tipo) {
        var task = pasta(tipo, 50L, 10L); listar(tipo, List.of(task));
        assertThrows(ResponseStatusException.class, () -> service.criar(task.getContratoTask(), task.getPipelineTarefa(), new Usuario(), " documentos ", null));
        verify(repository, never()).save(any());
    }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void rejeitaPastasDeOutraTarefaOuOutroTipo(TarefaTipo tipo) {
        when(repository.findById(50L)).thenReturn(Optional.of(pasta(tipo, 50L, 99L)));
        assertThrows(ResponseStatusException.class, () -> service.exigir(tipo, 10L, 50L));
        when(repository.findById(50L)).thenReturn(Optional.of(pasta(tipo == TarefaTipo.CONTRATO ? TarefaTipo.COMERCIAL : TarefaTipo.CONTRATO, 50L, 10L)));
        assertThrows(ResponseStatusException.class, () -> service.exigir(tipo, 10L, 50L));
        assertNull(service.exigir(tipo, 10L, null));
    }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void rejeitaNomesVaziosLongosOuComSeparadores(TarefaTipo tipo) {
        var task = pasta(tipo, 50L, 10L);
        for (String nome : List.of(" ", ".", "..", "a/b", "a\\b", "a\nb", "a".repeat(121))) {
            assertThrows(ResponseStatusException.class, () -> service.criar(task.getContratoTask(), task.getPipelineTarefa(), new Usuario(), nome, null));
        }
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void reutilizaAnexosAutomaticosSomenteNaRaiz(TarefaTipo tipo) {
        var task = pasta(tipo, 50L, 10L); task.setNome("Anexos"); listar(tipo, List.of(task));
        assertSame(task, service.anexosAutomaticos(task.getContratoTask(), task.getPipelineTarefa(), new Usuario()));
        verify(repository, never()).save(any());
    }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void criaAnexosAutomaticosQuandoAindaNaoExiste(TarefaTipo tipo) {
        var task = pasta(tipo, 50L, 10L); listar(tipo, List.of());
        var autor = new Usuario(); autor.setId(2L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var criada = service.anexosAutomaticos(task.getContratoTask(), task.getPipelineTarefa(), autor);
        assertEquals("Anexos", criada.getNome()); assertNull(criada.getPastaPai()); assertSame(autor, criada.getAutor());
        assertSame(task.getContratoTask(), criada.getContratoTask()); assertSame(task.getPipelineTarefa(), criada.getPipelineTarefa());
    }
}
