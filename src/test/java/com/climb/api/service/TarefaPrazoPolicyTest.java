package com.climb.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class TarefaPrazoPolicyTest {
    private final LocalDate hoje = LocalDate.of(2026, 11, 10);
    @Test void prazoSoAtrasaNoDiaSeguinteAoVencimento() {
        assertThat(TarefaPrazoPolicy.justificarConclusao(hoje, null, hoje)).isNull();
        assertThat(TarefaPrazoPolicy.justificarConclusao(hoje.plusDays(1), null, hoje)).isNull();
        assertThatThrownBy(() -> TarefaPrazoPolicy.justificarConclusao(hoje.minusDays(1), " \n ", hoje))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Conte o motivo");
    }
    @Test void justificativaEObrigatoriaENormalizada() {
        assertThat(TarefaPrazoPolicy.justificarConclusao(hoje.minusDays(1), "  Aguardando documentos do cliente.  ", hoje))
                .isEqualTo("Aguardando documentos do cliente.");
        assertThatThrownBy(() -> TarefaPrazoPolicy.justificarConclusao(hoje.minusDays(1), "a".repeat(4001), hoje))
                .isInstanceOf(ResponseStatusException.class);
    }
}
