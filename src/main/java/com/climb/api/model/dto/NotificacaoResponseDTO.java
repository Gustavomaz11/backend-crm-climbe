package com.climb.api.model.dto;

import com.climb.api.model.Notificacao;
import com.climb.api.model.Usuario;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NotificacaoResponseDTO {

    private Long id;
    private Long usuarioId;
    private String usuarioNome;
    private String usuarioEmail;
    private String mensagem;
    private LocalDate dataEnvio;
    private String tipo;
    private Boolean lida;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataLeitura;

    public static NotificacaoResponseDTO from(Notificacao notificacao) {
        NotificacaoResponseDTO dto = new NotificacaoResponseDTO();
        dto.setId(notificacao.getIdNotificacao());
        Usuario usuario = notificacao.getUsuario();
        if (usuario != null) {
            dto.setUsuarioId(usuario.getId());
            dto.setUsuarioNome(usuario.getNomeCompleto());
            dto.setUsuarioEmail(usuario.getEmail());
        }
        dto.setMensagem(notificacao.getMensagem());
        dto.setDataEnvio(notificacao.getDataEnvio());
        dto.setTipo(notificacao.getTipo());
        dto.setLida(Boolean.TRUE.equals(notificacao.getLida()));
        dto.setDataCriacao(notificacao.getDataCriacao());
        dto.setDataLeitura(notificacao.getDataLeitura());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String usuarioNome) { this.usuarioNome = usuarioNome; }

    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public LocalDate getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDate dataEnvio) { this.dataEnvio = dataEnvio; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Boolean getLida() { return lida; }
    public void setLida(Boolean lida) { this.lida = lida; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataLeitura() { return dataLeitura; }
    public void setDataLeitura(LocalDateTime dataLeitura) { this.dataLeitura = dataLeitura; }
}
