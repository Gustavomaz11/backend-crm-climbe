package com.climb.api.controller;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineCancelamentoRelatorioService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/pipeline-vendas/dashboard/cancelamentos")
public class PipelineCancelamentoRelatorioController {
    private final PipelineCancelamentoRelatorioService service;
    private final AuthenticatedUserProvider user;
    public PipelineCancelamentoRelatorioController(PipelineCancelamentoRelatorioService service, AuthenticatedUserProvider user) { this.service = service; this.user = user; }
    @GetMapping public ApiResponse<PipelineCancelamentoRelatorioService.Relatorio> buscar(
            @RequestParam(required = false) LocalDate inicio, @RequestParam(required = false) LocalDate fim,
            @RequestParam(required = false) Long campanhaId, @RequestParam(required = false) Long responsavelId) {
        return ApiResponse.ok(service.buscar(user.getUserId(), inicio, fim, campanhaId, responsavelId));
    }
}
