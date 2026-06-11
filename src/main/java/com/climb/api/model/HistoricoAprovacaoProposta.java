package com.climb.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "historico_aprovacao_propostas")
public class HistoricoAprovacaoProposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historico")
    private Long idHistorico;

    @Column(name = "proposta_id", nullable = false)
    private Long propostaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "status_anterior", nullable = false)
    private String statusAnterior;

    @Column(name = "status_novo", nullable = false)
    private String statusNovo;

    @Column(name = "data_alteracao", nullable = false)
    private LocalDateTime dataAlteracao;

    public Long getIdHistorico() {
        return idHistorico;
    }

    public void setIdHistorico(Long idHistorico) {
        this.idHistorico = idHistorico;
    }

    public Long getPropostaId() {
        return propostaId;
    }

    public void setPropostaId(Long propostaId) {
        this.propostaId = propostaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getStatusAnterior() {
        return statusAnterior;
    }

    public void setStatusAnterior(String statusAnterior) {
        this.statusAnterior = statusAnterior;
    }

    public String getStatusNovo() {
        return statusNovo;
    }

    public void setStatusNovo(String statusNovo) {
        this.statusNovo = statusNovo;
    }

    public LocalDateTime getDataAlteracao() {
        return dataAlteracao;
    }

    public void setDataAlteracao(LocalDateTime dataAlteracao) {
        this.dataAlteracao = dataAlteracao;
    }
}