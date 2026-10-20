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
    @Column(name = "permissoes_tentar_em", nullable = false)
    private java.time.LocalDateTime permissoesTentarEm = java.time.LocalDateTime.now();
    @Column(name = "permissoes_sincronizadas_em") private java.time.LocalDateTime permissoesSincronizadasEm;
    @Column(name = "permissoes_erro", length = 1000) private String permissoesErro;
}
