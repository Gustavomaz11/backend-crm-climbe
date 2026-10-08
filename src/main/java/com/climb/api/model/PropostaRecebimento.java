package com.climb.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
public class PropostaRecebimento {
    @Column(nullable = false)
    private Integer numero;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    public PropostaRecebimento() {}

    public PropostaRecebimento(Integer numero, BigDecimal valor) {
        this.numero = numero;
        this.valor = valor;
    }

    public Integer getNumero() { return numero; }
    public BigDecimal getValor() { return valor; }
}
