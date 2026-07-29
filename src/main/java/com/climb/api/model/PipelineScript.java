package com.climb.api.model;

import com.climb.api.model.enums.PipelineScriptCanal;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_scripts")
public class PipelineScript {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_script")
    private Long idScript;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false, length = 100)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PipelineScriptCanal canal;

    @Column(name = "modelo_mensagem", nullable = false, columnDefinition = "TEXT")
    private String modeloMensagem;

    @Column(nullable = false)
    private Boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() { criadoEm = atualizadoEm = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { atualizadoEm = LocalDateTime.now(); }

    public Long getIdScript() { return idScript; }
    public void setIdScript(Long idScript) { this.idScript = idScript; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public PipelineScriptCanal getCanal() { return canal; }
    public void setCanal(PipelineScriptCanal canal) { this.canal = canal; }
    public String getModeloMensagem() { return modeloMensagem; }
    public void setModeloMensagem(String modeloMensagem) { this.modeloMensagem = modeloMensagem; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
