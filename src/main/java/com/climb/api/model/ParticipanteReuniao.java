package com.climb.api.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.climb.api.model.enums.RespostaConviteReuniao;
import java.time.LocalDateTime;

@Entity
@EntityListeners(com.climb.api.model.listener.AvisosPersistenciaListener.class)
@Table(name = "participantes_reuniao")
public class ParticipanteReuniao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "versao", nullable = false)
    private Long versao;

    @ManyToOne
    @JoinColumn(name = "id_reuniao")
    private Reuniao reuniao;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private RespostaConviteReuniao resposta = RespostaConviteReuniao.PENDENTE;

    @Column(name = "respondido_em")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime respondidoEm;

    @Column(name = "aceite_sincronizado_google", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean aceiteSincronizadoGoogle;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Reuniao getReuniao() { return reuniao; }
    public void setReuniao(Reuniao reuniao) { this.reuniao = reuniao; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public RespostaConviteReuniao getResposta() { return resposta; }
    public void setResposta(RespostaConviteReuniao resposta) { this.resposta = resposta; }
    public LocalDateTime getRespondidoEm() { return respondidoEm; }
    public void setRespondidoEm(LocalDateTime respondidoEm) { this.respondidoEm = respondidoEm; }
    public boolean isAceiteSincronizadoGoogle() { return aceiteSincronizadoGoogle; }
    public void setAceiteSincronizadoGoogle(boolean sincronizado) { this.aceiteSincronizadoGoogle = sincronizado; }
}
