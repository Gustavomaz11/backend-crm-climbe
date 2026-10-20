package com.climb.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "empresa_comprovantes", uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "tipo"}))
@Getter @Setter
public class EmpresaComprovante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false, length = 14) private String cnpj;
    @Column(nullable = false, length = 20) private String tipo;
    @Column(nullable = false, length = 24) private String status;
    @Column(length = 500) private String mensagem;
    @Column(name = "atualizado_em", nullable = false) private LocalDateTime atualizadoEm;
    @Column(name = "gerado_em") private LocalDateTime geradoEm;
    @Column(name = "drive_arquivo_id", length = 150) private String driveArquivoId;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(columnDefinition = "LONGBLOB") private byte[] conteudo;
    @Version private long versao;
}
