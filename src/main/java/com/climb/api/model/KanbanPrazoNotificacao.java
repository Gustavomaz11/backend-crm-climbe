package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kanban_prazo_notificacoes")
public class KanbanPrazoNotificacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 200)
    private String chave;
    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    protected KanbanPrazoNotificacao() {}
    public KanbanPrazoNotificacao(String chave, LocalDateTime enviadoEm) {
        this.chave = chave; this.enviadoEm = enviadoEm;
    }
    public String getChave() { return chave; }
}
