package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "empresa_drive") @Getter @Setter
public class EmpresaDrive {
    @Id @Column(name = "empresa_id") private Long empresaId;
    @Column(name = "pasta_id", length = 150) private String pastaId;
    @Column(name = "documentacoes_id", length = 150) private String documentacoesId;
    @Column(nullable = false) private boolean pendente = true;
    @Column(name = "tentar_em", nullable = false) private LocalDateTime tentarEm = LocalDateTime.now();
    @Column(length = 500) private String erro;
}
