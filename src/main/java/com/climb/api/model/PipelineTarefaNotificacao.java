package com.climb.api.model;

import com.climb.api.model.enums.PipelineTarefaNotificacaoTipo;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_tarefa_notificacoes")
public class PipelineTarefaNotificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarefa_id")
    private PipelineVendasTarefa tarefa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinatario_usuario_id", nullable = false)
    private Usuario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PipelineTarefaNotificacaoTipo tipo;

    @Column(nullable = false, length = 30)
    private String referencia;

    @Column(nullable = false, unique = true, length = 190)
    private String chave;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    public Long getId() { return id; }
    public PipelineVendasTarefa getTarefa() { return tarefa; }
    public void setTarefa(PipelineVendasTarefa tarefa) { this.tarefa = tarefa; }
    public Usuario getDestinatario() { return destinatario; }
    public void setDestinatario(Usuario destinatario) { this.destinatario = destinatario; }
    public PipelineTarefaNotificacaoTipo getTipo() { return tipo; }
    public void setTipo(PipelineTarefaNotificacaoTipo tipo) { this.tipo = tipo; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }
    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public void setEnviadoEm(LocalDateTime enviadoEm) { this.enviadoEm = enviadoEm; }
}
