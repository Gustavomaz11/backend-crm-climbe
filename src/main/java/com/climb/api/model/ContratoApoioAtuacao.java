package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contrato_apoio_atuacoes", indexes = {
        @Index(name = "idx_apoio_atuacao_contrato_periodo", columnList = "contrato_id,inicio,fim"),
        @Index(name = "idx_apoio_atuacao_tarefa", columnList = "task_id")
})
public class ContratoApoioAtuacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;
    @ManyToOne @JoinColumn(name = "task_id")
    private ContratoKanbanTask tarefa;
    @ManyToOne(optional = false) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false)
    private LocalDateTime inicio;
    private LocalDateTime fim;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }
    public ContratoKanbanTask getTarefa() { return tarefa; }
    public void setTarefa(ContratoKanbanTask tarefa) { this.tarefa = tarefa; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getFim() { return fim; }
    public void setFim(LocalDateTime fim) { this.fim = fim; }
}
