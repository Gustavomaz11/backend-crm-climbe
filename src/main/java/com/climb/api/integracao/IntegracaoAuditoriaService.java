package com.climb.api.integracao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class IntegracaoAuditoriaService {
    private final IntegracaoAuditoriaRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String id, String evento, Long ator, String metodo, String rota, Integer status) {
        var item = new IntegracaoAuditoria();
        item.setIntegracaoId(id); item.setEvento(evento); item.setAtorId(ator);
        item.setMetodo(metodo); item.setRota(rota); item.setStatus(status);
        repository.save(item);
    }
}
