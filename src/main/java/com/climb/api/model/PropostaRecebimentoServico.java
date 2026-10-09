package com.climb.api.model;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

@Embeddable
public class PropostaRecebimentoServico {
    @Column(nullable = false)
    private Integer numero;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ServicoComercial servico;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    public PropostaRecebimentoServico() {}

    public PropostaRecebimentoServico(Integer numero, ServicoComercial servico, BigDecimal valor) {
        this.numero = numero;
        this.servico = servico;
        this.valor = valor;
    }

    public Integer getNumero() { return numero; }
    public ServicoComercial getServico() { return servico; }
    public BigDecimal getValor() { return valor; }
}
