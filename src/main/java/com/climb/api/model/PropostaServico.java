package com.climb.api.model;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

@Embeddable
public class PropostaServico {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ServicoComercial servico;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;
    @Column(name = "comissao_tecnico_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal comissaoTecnicoPercentual;
    @Column(name = "comissao_comercial_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal comissaoComercialPercentual;

    public PropostaServico() {}

    public PropostaServico(ServicoComercial servico, BigDecimal valor, BigDecimal tecnico, BigDecimal comercial) {
        this.servico = servico;
        this.valor = valor;
        this.comissaoTecnicoPercentual = tecnico;
        this.comissaoComercialPercentual = comercial;
    }

    public ServicoComercial getServico() { return servico; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getComissaoTecnicoPercentual() { return comissaoTecnicoPercentual; }
    public BigDecimal getComissaoComercialPercentual() { return comissaoComercialPercentual; }
}
