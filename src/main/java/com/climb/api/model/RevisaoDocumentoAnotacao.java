package com.climb.api.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "revisoes_documento_anotacoes")
public class RevisaoDocumentoAnotacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "versao_id", nullable = false)
    private RevisaoDocumentoVersao versao;

    @Column(nullable = false)
    private int pagina;

    @Column(name = "posicao_x", nullable = false, precision = 10, scale = 8)
    private BigDecimal posicaoX;
    @Column(name = "posicao_y", nullable = false, precision = 10, scale = 8)
    private BigDecimal posicaoY;
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal largura;
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal altura;
    @Column(nullable = false, length = 20)
    private String cor;
    @Lob
    @Column(nullable = false)
    private String comentario;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RevisaoDocumentoVersao getVersao() { return versao; }
    public void setVersao(RevisaoDocumentoVersao versao) { this.versao = versao; }
    public int getPagina() { return pagina; }
    public void setPagina(int pagina) { this.pagina = pagina; }
    public BigDecimal getPosicaoX() { return posicaoX; }
    public void setPosicaoX(BigDecimal posicaoX) { this.posicaoX = posicaoX; }
    public BigDecimal getPosicaoY() { return posicaoY; }
    public void setPosicaoY(BigDecimal posicaoY) { this.posicaoY = posicaoY; }
    public BigDecimal getLargura() { return largura; }
    public void setLargura(BigDecimal largura) { this.largura = largura; }
    public BigDecimal getAltura() { return altura; }
    public void setAltura(BigDecimal altura) { this.altura = altura; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
