package com.climb.api.model;

import com.climb.api.model.enums.ServicoComercial;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "empresa_drive_servicos", uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "servico"}))
@Getter @Setter
public class EmpresaDriveServico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private ServicoComercial servico;
    @Column(name = "pasta_id", length = 150) private String pastaId;
}
