package com.climb.api.model.listener;

import com.climb.api.model.*;
import com.climb.api.service.*;
import jakarta.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class DrivePermissoesPersistenciaListener {
    private final ApplicationEventPublisher eventos;
    public DrivePermissoesPersistenciaListener(ApplicationEventPublisher eventos) { this.eventos = eventos; }
    @PostPersist @PostUpdate @PostRemove
    public void alterado(Object entidade) {
        if (entidade instanceof Usuario) { eventos.publishEvent(new DrivePermissoesAlteradas(null)); return; }
        Contrato contrato = entidade instanceof Contrato c ? c
                : entidade instanceof ContratoEquipeMembro m ? m.getContrato()
                : entidade instanceof ContratoKanbanTask t ? t.getContrato()
                : entidade instanceof ContratoKanbanRaia r ? r.getContrato() : null;
        if (contrato != null && contrato.getEmpresa() != null)
            eventos.publishEvent(new DrivePermissoesAlteradas(contrato.getEmpresa().getIdEmpresa()));
        if (entidade instanceof ContratoEquipeMembro m)
            eventos.publishEvent(new AvisoAtualizado(m.getUsuario().getId(), null, AvisoAtualizado.Tipo.KANBAN));
    }
}
