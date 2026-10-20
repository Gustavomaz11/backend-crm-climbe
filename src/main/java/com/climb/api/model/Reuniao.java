package com.climb.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@EntityListeners(com.climb.api.model.listener.AvisosPersistenciaListener.class)
@Table(name = "reunioes")
public class Reuniao extends ItemArquivavelKanban {

    @Override
    public boolean podeArquivar() {
        return "REALIZADA".equals(status) || "REALIZADO".equals(status)
                || "CONCLUIDA".equals(status) || "CONCLUIDO".equals(status);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reuniao")
    private Long idReuniao;

    @Column(nullable = false)
    private String titulo;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(name = "data")
    private LocalDate data;

    @Column(name = "hora")
    private LocalTime hora;

    @Column(name = "data_hora_fim")
    private LocalDateTime dataHoraFim;

    @Column(name = "presencial")
    private Boolean presencial;

    @Column(name = "local")
    private String local;

    @Column(name = "sala_email", length = 320)
    private String salaEmail;

    @Column(name = "pauta", columnDefinition = "TEXT")
    private String pauta;

    @Column(name = "status")
    private String status;

    @Column(name = "google_event_id")
    private String googleEventId;

    @Column(name = "organizador_id")
    private Long organizadorId;

    @Column(name = "link_meet", length = 1024)
    private String linkMeet;

    @Column(name = "convites_notificados", nullable = false)
    private boolean convitesNotificados;

    public Long getIdReuniao() { return idReuniao; }
    public void setIdReuniao(Long idReuniao) { this.idReuniao = idReuniao; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public LocalDateTime getDataHoraFim() {
        if (dataHoraFim != null) return dataHoraFim;
        return data == null || hora == null ? null : data.atTime(hora).plusHours(1);
    }
    public void setDataHoraFim(LocalDateTime dataHoraFim) { this.dataHoraFim = dataHoraFim; }

    public Boolean getPresencial() { return presencial; }
    public void setPresencial(Boolean presencial) { this.presencial = presencial; }

    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }

    public String getSalaEmail() { return salaEmail; }
    public void setSalaEmail(String salaEmail) { this.salaEmail = salaEmail; }

    public String getPauta() { return pauta; }
    public void setPauta(String pauta) { this.pauta = pauta; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGoogleEventId() { return googleEventId; }
    public Long getOrganizadorId() { return organizadorId; }
    public void setOrganizadorId(Long organizadorId) { this.organizadorId = organizadorId; }
    public String getLinkMeet() { return linkMeet; }
    public boolean isConvitesNotificados() { return convitesNotificados; }
    public void setConvitesNotificados(boolean convitesNotificados) { this.convitesNotificados = convitesNotificados; }
    public void setLinkMeet(String linkMeet) { this.linkMeet = linkMeet; }
    public void setGoogleEventId(String googleEventId) { this.googleEventId = googleEventId; }
}
