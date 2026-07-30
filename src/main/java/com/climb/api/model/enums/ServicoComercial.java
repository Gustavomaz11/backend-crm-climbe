package com.climb.api.model.enums;

import java.util.EnumSet;

public enum ServicoComercial {
    BPO,
    CFO,
    CONTABILIDADE,
    VALUATION,
    FINANCE_SUPPORT,
    CONSORCIO,
    DESENVOLVIMENTO_SOFTWARE;

    private static final EnumSet<ServicoComercial> RECORRENTES = EnumSet.of(
            BPO, CFO, CONTABILIDADE, FINANCE_SUPPORT, DESENVOLVIMENTO_SOFTWARE
    );

    public boolean recorrente() {
        return RECORRENTES.contains(this);
    }
}
