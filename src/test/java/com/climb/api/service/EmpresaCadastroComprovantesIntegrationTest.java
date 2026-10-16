package com.climb.api.service;

import com.climb.api.model.dto.EmpresaRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.EmpresaComprovanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.mail.host=localhost", "logging.level.root=WARN",
        "app.cnpja.comprovantes-interval-ms=86400000", "app.pipeline.cadencia.atraso-inicial-ms=86400000",
        "app.pipeline.task-reminders.weekly-cron=-", "app.pipeline.task-reminders.overdue-cron=-"})
@ActiveProfiles("test") @Transactional
class EmpresaCadastroComprovantesIntegrationTest {
    @Autowired EmpresaService empresas;
    @Autowired EmpresaRepository repository;
    @Autowired EmpresaComprovanteRepository documentos;
    @Autowired EmpresaComprovanteService comprovantes;
    @Autowired EmpresaComprovanteProcessador processador;
    @Autowired EntityManager em;
    @MockitoBean CnpjaService cnpja;

    @Test void salvaDadosAtuaisSemHistoricoEEmitePdfsSomenteDepoisDePersistirEmpresa() {
        var dados = Map.<String, Object>of("company", Map.of("simples", Map.of("optant", true, "history", List.of(Map.of("since", "2000-01-01"))),
                "simei", Map.of("optant", true)), "status", Map.of("id", 2, "text", "Ativa"), "mainActivity", Map.of("id", 6201501, "text", "Desenvolvimento"));
        var request = new EmpresaRequestDTO("Empresa Ltda.", "Empresa", "37.335.118/0001-80", "Rua", "100", "Centro", "Aracaju", "SE", "49010-000",
                "79999991234", "contato@example.test", "Ana", "111.111.111-11", "ana@example.test", "MEI", dados);
        var result = empresas.criar(request);
        em.flush(); em.clear();
        var empresa = repository.findById(result.id()).orElseThrow();
        assertThat(empresa.getTipoEmpresa()).isEqualTo("MEI");
        assertThat(empresa.getDadosCadastrais().toString()).doesNotContain("history", "2000-01-01");
        assertThat(comprovantes.listar(result.id())).hasSize(4).allMatch(d -> d.status().equals("PENDENTE"));
        verifyNoInteractions(cnpja);

        byte[] pdf = "%PDF-1.4\ncomprovante de teste\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        when(cnpja.emitirComprovante(anyString(), eq("37335118000180"))).thenReturn(pdf);
        for (int i = 0; i < 4; i++) processador.processar();
        em.clear();
        assertThat(comprovantes.listar(result.id())).allMatch(d -> d.status().equals("DISPONIVEL"));
        var id = comprovantes.listar(result.id()).getFirst().id();
        assertThat(comprovantes.buscar(result.id(), id).getConteudo()).isEqualTo(pdf);
        assertThat(documentos.findByIdAndEmpresaId(id, result.id() + 1)).isEmpty();
        comprovantes.solicitar(result.id());
        assertThat(comprovantes.listar(result.id())).hasSize(4).allMatch(d -> d.status().equals("DISPONIVEL"));
        verify(cnpja, times(4)).emitirComprovante(anyString(), anyString());
    }
}
