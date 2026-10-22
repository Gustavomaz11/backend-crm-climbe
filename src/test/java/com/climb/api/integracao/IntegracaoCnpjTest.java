package com.climb.api.integracao;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IntegracaoCnpjTest {
    @Test void validaFormatosNumericoEAlfanumericoComDigitosVerificadores() {
        assertThat(IntegracaoCnpj.valido("04.252.011/0001-10")).isTrue();
        assertThat(IntegracaoCnpj.valido("12.ABC.345/01DE-35")).isTrue();
        assertThat(IntegracaoCnpj.valido("12abc34501de35")).isTrue();
        assertThat(IntegracaoCnpj.valido("12ABC34501DE34")).isFalse();
        assertThat(IntegracaoCnpj.valido("00000000000000")).isFalse();
        assertThat(IntegracaoCnpj.valido("04252011000111")).isFalse();
    }
}
