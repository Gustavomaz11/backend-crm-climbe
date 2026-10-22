package com.climb.api.integracao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "integracao_auditoria") @Getter @Setter
public class IntegracaoAuditoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "integracao_id", nullable = false, length = 36) private String integracaoId;
    @Column(nullable = false, length = 100) private String evento;
    @Column(name = "ator_id") private Long atorId;
    @Column(length = 10) private String metodo;
    @Column(length = 255) private String rota;
    private Integer status;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm = Instant.now();
}
