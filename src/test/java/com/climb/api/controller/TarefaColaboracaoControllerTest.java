package com.climb.api.controller;

import com.climb.api.model.dto.*;
import com.climb.api.model.enums.TarefaTipo;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.TarefaColaboracaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TarefaColaboracaoControllerTest {
    @Mock private TarefaColaboracaoService service;
    @Mock private AuthenticatedUserProvider auth;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(new TarefaColaboracaoController(service, auth))
                .setMessageConverters(new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter(mapper)).build();
        when(auth.getUserId()).thenReturn(7L);
    }

    @ParameterizedTest
    @EnumSource(TarefaTipo.class)
    void recebeVariosArquivosComUsuarioAutenticado(TarefaTipo tipo) throws Exception {
        when(service.anexar(eq(tipo), eq(10L), eq(7L), anyList(), eq(50L))).thenReturn(List.of(anexo()));
        mvc.perform(multipart(path(tipo) + "/anexos").file(arquivo("balanco.pdf")).file(arquivo("outro.pdf")).param("pastaId", "50"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data[0].nome").value("balanco.pdf"));
        verify(service).anexar(eq(tipo), eq(10L), eq(7L), argThat(files -> files.size() == 2
                && "balanco.pdf".equals(files.get(0).getOriginalFilename()) && "outro.pdf".equals(files.get(1).getOriginalFilename())), eq(50L));
    }

    @ParameterizedTest
    @EnumSource(TarefaTipo.class)
    void recebeRespostaComAnexoEComentarioSemAnexo(TarefaTipo tipo) throws Exception {
        var resposta = new TarefaComentarioResponseDTO(30L, 20L, anexo().autor(), "Conferido", LocalDateTime.now(), List.of(anexo()));
        when(service.comentar(eq(tipo), eq(10L), eq(7L), eq("Conferido"), eq(20L), anyList())).thenReturn(resposta);
        mvc.perform(multipart(path(tipo) + "/comentarios").file(arquivo("balanco.pdf"))
                        .param("conteudo", "Conferido").param("comentarioPaiId", "20"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.comentarioPaiId").value(20))
                .andExpect(jsonPath("$.data.anexos[0].id").value(40));
        when(service.comentar(tipo, 10L, 7L, "Sem arquivo", null, null)).thenReturn(resposta);
        mvc.perform(multipart(path(tipo) + "/comentarios").param("conteudo", "Sem arquivo"))
                .andExpect(status().isCreated());
        verify(service).comentar(tipo, 10L, 7L, "Sem arquivo", null, null);
    }

    @ParameterizedTest
    @EnumSource(TarefaTipo.class)
    void entregaConteudoPrivadoParaVisualizarOuBaixar(TarefaTipo tipo) throws Exception {
        byte[] bytes = "%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.baixar(tipo, 10L, 40L, 7L)).thenReturn(new TarefaColaboracaoService.ConteudoAnexo(anexo(), bytes));
        mvc.perform(get(path(tipo) + "/anexos/40/conteudo"))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(path(tipo) + "/anexos/40/conteudo").param("download", "true"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @ParameterizedTest
    @EnumSource(TarefaTipo.class)
    void naoEntregaArquivoQuandoAcessoNegado(TarefaTipo tipo) throws Exception {
        when(service.baixar(tipo, 10L, 40L, 7L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(get(path(tipo) + "/anexos/40/conteudo")).andExpect(status().isForbidden());
    }

    private String path(TarefaTipo tipo) { return "/tarefas/" + tipo + "/10/colaboracao"; }
    @ParameterizedTest @EnumSource(TarefaTipo.class)
    void recebeNomeEPastaPaiParaCriarSubpasta(TarefaTipo tipo) throws Exception {
        var dto = new TarefaPastaRequestDTO("Documentos", 50L);
        when(service.criarPasta(tipo, 10L, 7L, dto)).thenReturn(new TarefaPastaResponseDTO(51L, "Documentos", 50L, anexo().autor(), LocalDateTime.now()));
        mvc.perform(post(path(tipo) + "/pastas").contentType("application/json").content("{\"nome\":\"Documentos\",\"pastaPaiId\":50}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.pastaPaiId").value(50));
        verify(service).criarPasta(tipo, 10L, 7L, dto);
    }
    private MockMultipartFile arquivo(String nome) { return new MockMultipartFile("arquivos", nome, "application/pdf", new byte[]{1}); }
    private TarefaAnexoResponseDTO anexo() {
        return new TarefaAnexoResponseDTO(40L, "balanco.pdf", "application/pdf", 4L,
                new UsuarioResumoDTO(7L, "Ana", "ana@example.test"), LocalDateTime.now());
    }
}
