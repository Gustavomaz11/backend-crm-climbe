package com.climb.api.service;

import com.climb.api.model.PipelineVendasComentario;
import com.climb.api.model.PipelineVendasNegocio;
import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.PipelineComentarioRequestDTO;
import com.climb.api.repository.PipelineVendasComentarioRepository;
import com.climb.api.repository.PipelineVendasNegocioRepository;
import com.climb.api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineComentarioServiceTest {
    @Mock private PipelineVendasComentarioRepository repository;
    @Mock private PipelineVendasNegocioRepository negocioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PipelineHistoricoService historicoService;
    @Mock private RbacService rbacService;

    private PipelineComentarioService service;

    @BeforeEach
    void setUp() {
        service = new PipelineComentarioService(
                repository, negocioRepository, usuarioRepository, historicoService, rbacService
        );
    }

    @Test
    void deveCriarRespostaVinculadaAoComentarioDoMesmoNegocio() {
        PipelineVendasNegocio negocio = negocio(10L);
        PipelineVendasComentario comentarioPai = comentario(20L, negocio, usuario(2L, "Maria"));
        Usuario autor = usuario(1L, "Gustavo");
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_COMENTARIO_CRIAR)).thenReturn(true);
        when(negocioRepository.findById(10L)).thenReturn(Optional.of(negocio));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(autor));
        when(repository.findById(20L)).thenReturn(Optional.of(comentarioPai));
        when(repository.save(any())).thenAnswer(invocation -> {
            PipelineVendasComentario resposta = invocation.getArgument(0);
            resposta.setIdComentario(21L);
            return resposta;
        });

        var response = service.criar(
                10L, 1L, new PipelineComentarioRequestDTO("Concordo com o ajuste", 20L)
        );

        ArgumentCaptor<PipelineVendasComentario> captor = ArgumentCaptor.forClass(PipelineVendasComentario.class);
        verify(repository).save(captor.capture());
        assertEquals(20L, captor.getValue().getComentarioPai().getIdComentario());
        assertEquals(20L, response.comentarioPaiId());
    }

    @Test
    void deveImpedirRespostaEmComentarioDeOutroNegocio() {
        PipelineVendasNegocio negocio = negocio(10L);
        PipelineVendasComentario comentarioPai = comentario(20L, negocio(11L), usuario(2L, "Maria"));
        when(rbacService.temPermissao(1L, PermissaoCodigo.COMERCIAL_COMENTARIO_CRIAR)).thenReturn(true);
        when(negocioRepository.findById(10L)).thenReturn(Optional.of(negocio));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "Gustavo")));
        when(repository.findById(20L)).thenReturn(Optional.of(comentarioPai));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.criar(10L, 1L, new PipelineComentarioRequestDTO("Resposta", 20L))
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    private PipelineVendasComentario comentario(Long id, PipelineVendasNegocio negocio, Usuario autor) {
        PipelineVendasComentario comentario = new PipelineVendasComentario();
        comentario.setIdComentario(id);
        comentario.setNegocio(negocio);
        comentario.setAutor(autor);
        comentario.setConteudo("Comentário original");
        return comentario;
    }

    private PipelineVendasNegocio negocio(Long id) {
        PipelineVendasNegocio negocio = new PipelineVendasNegocio();
        negocio.setIdNegocio(id);
        return negocio;
    }

    private Usuario usuario(Long id, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNomeCompleto(nome);
        return usuario;
    }
}
