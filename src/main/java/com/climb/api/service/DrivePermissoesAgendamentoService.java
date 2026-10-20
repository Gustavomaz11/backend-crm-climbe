package com.climb.api.service;

import com.climb.api.repository.EmpresaDriveServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;

@Service
public class DrivePermissoesAgendamentoService {
    private final EmpresaDriveServicoRepository pastas;
    public DrivePermissoesAgendamentoService(EmpresaDriveServicoRepository pastas) { this.pastas = pastas; }
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void agendar(DrivePermissoesAlteradas evento) { pastas.agendarPermissoes(evento.empresaId(), LocalDateTime.now()); }
}
