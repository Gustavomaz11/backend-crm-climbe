package com.climb.api.service;

import com.climb.api.model.dto.PropostaComercialRequestDTO;
import com.climb.api.model.dto.PropostaRecebimentoDTO;
import com.climb.api.model.dto.PropostaServicoDTO;
import com.climb.api.model.enums.ServicoComercial;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class PropostaComercialValidatorTest {
    private final BigDecimal total = new BigDecimal("50000.00");
    private final List<PropostaServicoDTO> servicos = List.of(
            new PropostaServicoDTO(ServicoComercial.BPO, new BigDecimal("30000.00"), new BigDecimal("25.00"), null),
            new PropostaServicoDTO(ServicoComercial.CFO, new BigDecimal("20000.00"), null, new BigDecimal("10.00")));

    @Test
    void deveAceitarMultiplosServicosERecebimentosPersonalizados() {
        assertDoesNotThrow(() -> PropostaComercialValidator.validar(config(servicos, recebimentos()), total));
    }

    @Test
    void deveRecusarDistribuicaoIncompletaDosServicos() {
        assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config(List.of(servicos.getFirst()), recebimentos()), total));
    }

    @Test
    void deveRecusarServicosDuplicadosEComissoesInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config(List.of(servicos.getFirst(), servicos.getFirst()), recebimentos()), total));
        var invalido = new PropostaServicoDTO(ServicoComercial.CFO, new BigDecimal("20000.00"), new BigDecimal("101"), null);
        assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config(List.of(servicos.getFirst(), invalido), recebimentos()), total));
    }

    @Test
    void deveRecusarRecebimentosComSomaOuNumeracaoIncorreta() {
        var incorretos = IntStream.rangeClosed(1, 12).mapToObj(numero -> new PropostaRecebimentoDTO(numero, new BigDecimal("3000.00"))).toList();
        assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config(servicos, incorretos), total));
        var duplicados = IntStream.rangeClosed(1, 12).mapToObj(numero -> new PropostaRecebimentoDTO(1, numero <= 2 ? new BigDecimal("3000.00") : new BigDecimal("4400.00"))).toList();
        assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config(servicos, duplicados), total));
        assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config(servicos, recebimentos().subList(0, 11)), total));
    }

    @Test
    void devePreservarConfiguracaoLegadaDeUmServico() {
        var legado = new PropostaComercialRequestDTO(ServicoComercial.BPO, null, 0, 1, true,
                null, null, List.of(), List.of(), List.of(), null);
        assertDoesNotThrow(() -> PropostaComercialValidator.validar(legado, total));
    }

    @Test
    void deveExigirQuantidadeParaPlanoExplicitoDeRecebimentos() {
        var config = new PropostaComercialRequestDTO(ServicoComercial.BPO, LocalDate.of(2026, 10, 1), 1, null,
                true, null, null, List.of(), List.of(), List.of(), null,
                List.of(new PropostaServicoDTO(ServicoComercial.BPO, total, null, null)),
                List.of(new PropostaRecebimentoDTO(1, total)));
        var erro = assertThrows(IllegalArgumentException.class, () -> PropostaComercialValidator.validar(config, total));
        assertEquals("Informe a quantidade de recebimentos da proposta.", erro.getMessage());
    }

    static List<PropostaRecebimentoDTO> recebimentos() {
        return IntStream.rangeClosed(1, 12).mapToObj(numero -> new PropostaRecebimentoDTO(numero,
                numero <= 2 ? new BigDecimal("3000.00") : new BigDecimal("4400.00"))).toList();
    }

    private PropostaComercialRequestDTO config(List<PropostaServicoDTO> servicos, List<PropostaRecebimentoDTO> recebimentos) {
        return new PropostaComercialRequestDTO(ServicoComercial.BPO, LocalDate.of(2026, 10, 1), 12, 12,
                false, null, null, List.of(), List.of(), List.of(), null, servicos, recebimentos);
    }
}
