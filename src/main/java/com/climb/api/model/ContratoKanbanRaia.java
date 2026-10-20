package com.climb.api.model;

import jakarta.persistence.*;
import com.climb.api.model.enums.ServicoComercial;
import java.time.LocalDateTime;

@Entity
@jakarta.persistence.EntityListeners(com.climb.api.model.listener.DrivePermissoesPersistenciaListener.class)
@Table(name = "contrato_kanban_raias")
public class ContratoKanbanRaia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_raia")
    private Long idRaia;

    @ManyToOne
    @JoinColumn(name = "contrato_id", nullable = false)
    private Contrato contrato;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ServicoComercial servico;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false)
    private Integer posicao = 0;
    @Column(name = "conclui_tarefas", nullable = false)
    private boolean concluiTarefas;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public Long getIdRaia() { return idRaia; }
    public void setIdRaia(Long idRaia) { this.idRaia = idRaia; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public ServicoComercial getServico() { return servico; }
    public void setServico(ServicoComercial servico) { this.servico = servico; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public Integer getPosicao() { return posicao; }
    public void setPosicao(Integer posicao) { this.posicao = posicao; }

    public boolean isConcluiTarefas() { return concluiTarefas; }
    public void setConcluiTarefas(boolean concluiTarefas) { this.concluiTarefas = concluiTarefas; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
