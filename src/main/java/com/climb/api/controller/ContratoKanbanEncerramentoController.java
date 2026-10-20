package com.climb.api.controller;

import com.climb.api.model.dto.ContratoServicoEncerramentoRequestDTO;
import com.climb.api.model.dto.ContratoServicoEncerramentoResponseDTO;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.ContratoServicoEncerramentoService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContratoKanbanEncerramentoController {
    private final ContratoServicoEncerramentoService encerramento;
    private final AuthenticatedUserProvider autenticado;

    public ContratoKanbanEncerramentoController(ContratoServicoEncerramentoService encerramento, AuthenticatedUserProvider autenticado) {
        this.encerramento = encerramento; this.autenticado = autenticado;
    }

    @PatchMapping("/contratos/{contratoId}/kanban/encerrar")
    public ContratoServicoEncerramentoResponseDTO encerrar(@PathVariable Long contratoId,
            @RequestBody ContratoServicoEncerramentoRequestDTO dto) {
        return encerramento.encerrar(contratoId, dto.servico(), autenticado.getUserId());
    }
}
