package com.climb.api.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ReuniaoRequestDTO {

    private String titulo;
    private Long empresaId;
    private LocalDate data;
    private LocalTime hora;
    private Boolean presencial;
    private String local;
    private String pauta;
    private String status;
    private List<Long> participanteIds;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public Long getEmpresaId() { return empresaId; }
    public void setEmpresaId(Long empresaId) { this.empresaId = empresaId; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public Boolean getPresencial() { return presencial; }
    public void setPresencial(Boolean presencial) { this.presencial = presencial; }

    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }

    public String getPauta() { return pauta; }
    public void setPauta(String pauta) { this.pauta = pauta; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Long> getParticipanteIds() { return participanteIds; }
    public void setParticipanteIds(List<Long> participanteIds) { this.participanteIds = participanteIds; }
}
