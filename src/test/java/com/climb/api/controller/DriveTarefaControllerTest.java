package com.climb.api.controller;

import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DriveTarefaControllerTest {
    private final DriveTarefaService service = mock(DriveTarefaService.class);
    private final AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
    private MockMvc mvc;
    @BeforeEach void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new DriveTarefaController(service, auth)).build();
        when(auth.getUserId()).thenReturn(7L);
    }

    @Test void listaConteudoDoServicoComIdsDoDriveComoTextoEUsuarioAutenticado() throws Exception {
        when(service.listar(10L, 7L, ServicoComercial.BPO, "ano")).thenReturn(List.of(
                new GoogleDriveClient.Arquivo("pdf-ID", "Balanço.pdf", "application/pdf", 3, List.of("ano"), false, null)));
        mvc.perform(get("/tarefas/CONTRATO/10/drive/BPO/arquivos").param("pastaId", "ano"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("pdf-ID"))
                .andExpect(jsonPath("$.data[0].contentType").value("application/pdf"));
    }

    @Test void criaPastaNoServicoInformado() throws Exception {
        mvc.perform(post("/tarefas/CONTRATO/10/drive/CFO/pastas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Relatórios\",\"pastaPaiId\":\"ano\"}"))
                .andExpect(status().isCreated());
        verify(service).criarPasta(10L, 7L, ServicoComercial.CFO, new DriveTarefaService.NovaPasta("Relatórios", "ano"));
    }

    @Test void baixaArquivoSemCacheComNomeOriginal() throws Exception {
        when(service.baixar(10L, 7L, ServicoComercial.BPO, "pdf-ID"))
                .thenReturn(new GoogleDriveClient.Conteudo("Balanço.pdf", "application/pdf", new byte[]{1, 2, 3}));
        mvc.perform(get("/tarefas/CONTRATO/10/drive/BPO/arquivos/pdf-ID/conteudo").param("download", "true"))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }
}
