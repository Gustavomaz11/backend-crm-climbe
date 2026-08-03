package com.climb.api.repository;

import com.climb.api.model.OAuthProvider;
import com.climb.api.model.enums.PipelineTarefaStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.mail.host=localhost"
})
@ActiveProfiles("test")
@Transactional
class PerformanceQueriesIntegrationTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioOAuthRepository usuarioOAuthRepository;
    @Autowired private PipelineVendasNegocioRepository negocioRepository;
    @Autowired private PipelineVendasTarefaRepository tarefaRepository;
    @Autowired private RevisaoDocumentoAnotacaoRepository anotacaoRepository;
    @Autowired private ContratoKanbanTaskRepository contratoTaskRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;

    @Test
    void deveExecutarConsultasOtimizadasEmBancoReal() {
        assertTrue(usuarioRepository.findCodigosPermissoesById(999L).isEmpty());
        assertTrue(usuarioOAuthRepository.findAvataresByUsuarioIdsAndProvider(
                List.of(999L), OAuthProvider.GOOGLE).isEmpty());
        assertTrue(negocioRepository.findDashboardNegocios(
                null, null, null, null, null, null, null, null, null).isEmpty());
        assertTrue(negocioRepository.findDashboardFilterOptions().isEmpty());
        assertTrue(negocioRepository
                .findByFunilIdFunilOrderByEtapaPosicaoAscUltimaMovimentacaoEmDesc(999L)
                .isEmpty());
        assertTrue(negocioRepository.findByResultadoOrderByCriadoEmDesc(
                com.climb.api.model.enums.PipelineVendasResultado.ABERTO).isEmpty());
        assertEquals(0, tarefaRepository.countByNegocioIdNegocioInAndPrazoBeforeAndStatusNotIn(
                List.of(999L), LocalDate.now(),
                List.of(PipelineTarefaStatus.CONCLUIDA, PipelineTarefaStatus.CANCELADA)));
        assertTrue(tarefaRepository.findFiltradas(
                false, false, true, false, false, LocalDate.now(), null, null, null, null).isEmpty());
        assertTrue(anotacaoRepository.findByRevisaoIdWithVersao(999L).isEmpty());
        assertTrue(contratoTaskRepository.findResponsavelIdsComTasks(999L, java.util.Set.of(999L)).isEmpty());
        assertEquals(0, notificacaoRepository.marcarTodasComoLidas(999L, java.time.LocalDateTime.now()));
    }
}
