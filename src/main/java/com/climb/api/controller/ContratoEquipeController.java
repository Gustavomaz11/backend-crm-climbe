package com.climb.api.controller;

import com.climb.api.model.Contrato;
import com.climb.api.model.dto.*;
import com.climb.api.service.ContratoEquipeService;
import com.climb.api.service.ContratoRateioTecnicoService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/contratos")
public class ContratoEquipeController {
    private final ContratoEquipeService service;
    private final ContratoRateioTecnicoService rateio;
    public ContratoEquipeController(ContratoEquipeService service,
            ContratoRateioTecnicoService rateio) { this.service = service; this.rateio = rateio; }

    @GetMapping("/kanban-disponiveis")
    public List<Contrato> listar() { return service.listarDisponiveis(usuarioId()); }

    @GetMapping("/{id}/equipe")
    public ContratoEquipeResponseDTO buscar(@PathVariable Long id) { return service.buscarEquipe(id, usuarioId()); }

    @PutMapping("/{id}/equipe")
    public ContratoEquipeResponseDTO salvar(@PathVariable Long id, @Valid @RequestBody ContratoEquipeRequestDTO dto) {
        return service.salvarEquipe(id, usuarioId(), dto.usuarioIds());
    }
    @GetMapping("/{id}/rateio-tecnico")
    public ContratoRateioTecnicoResponseDTO consultarRateio(@PathVariable Long id, @RequestParam String competencia) {
        return rateio.consultar(id, competencia, usuarioId());
    }
    private Long usuarioId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object details = authentication.getDetails();
        if (details instanceof Long id) return id;
        if (details instanceof Integer id) return id.longValue();
        try { return details instanceof String id ? Long.valueOf(id) : null; }
        catch (NumberFormatException e) { return null; }
    }
}
