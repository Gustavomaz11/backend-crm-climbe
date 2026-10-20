package com.climb.api.model.listener;

import com.climb.api.model.Notificacao;
import com.climb.api.model.ParticipanteReuniao;
import com.climb.api.model.Reuniao;
import com.climb.api.service.AvisoAtualizado;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AvisosPersistenciaListener {
    private final ApplicationEventPublisher eventos;

    public AvisosPersistenciaListener(ApplicationEventPublisher eventos) { this.eventos = eventos; }

    @PostPersist @PostUpdate @PostRemove
    public void alterado(Object entidade) {
        if (entidade instanceof Notificacao aviso && aviso.getUsuario() != null) {
            eventos.publishEvent(new AvisoAtualizado(aviso.getUsuario().getId(), null, AvisoAtualizado.Tipo.NOTIFICACOES));
            if (!Boolean.TRUE.equals(aviso.getLida()) && ("CONTRATO_APROVADO".equals(aviso.getTipo())
                    || ("TAREFA_ATRIBUIDA".equals(aviso.getTipo()) && aviso.getDestino() != null
                        && aviso.getDestino().startsWith("/contratos/kanban")))) {
                eventos.publishEvent(new AvisoAtualizado(aviso.getUsuario().getId(), null, AvisoAtualizado.Tipo.KANBAN));
            }
        } else if (entidade instanceof ParticipanteReuniao convite && convite.getUsuario() != null) {
            eventos.publishEvent(new AvisoAtualizado(convite.getUsuario().getId(), null, AvisoAtualizado.Tipo.CONVITES));
        } else if (entidade instanceof Reuniao reuniao) {
            eventos.publishEvent(new AvisoAtualizado(null, reuniao.getIdReuniao(), AvisoAtualizado.Tipo.CONVITES));
        }
    }
}
