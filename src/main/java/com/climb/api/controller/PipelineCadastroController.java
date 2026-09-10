package com.climb.api.controller;

import com.climb.api.model.*;
import com.climb.api.model.dto.ApiResponse;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.service.PipelineCadastroService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/pipeline-vendas/cadastros")
public class PipelineCadastroController {
    private final PipelineCadastroService service;
    private final AuthenticatedUserProvider user;
    public PipelineCadastroController(PipelineCadastroService service, AuthenticatedUserProvider user) { this.service = service; this.user = user; }
    public record TagInput(String nome, String cor, Boolean ativo) {}
    public record CampoInput(String nome, String tipo, String escopo, String opcoes, Boolean ativo) {}
    @GetMapping("/tags") public ApiResponse<List<PipelineTag>> tags() { return ApiResponse.ok(service.tags(user.getUserId())); }
    @GetMapping("/campos") public ApiResponse<List<PipelineCampo>> campos() { return ApiResponse.ok(service.campos(user.getUserId())); }
    @PostMapping("/tags") public ApiResponse<PipelineTag> tag(@RequestBody TagInput dto) { return salvarTag(null, dto); }
    @PutMapping("/tags/{id}") public ApiResponse<PipelineTag> salvarTag(@PathVariable Long id, @RequestBody TagInput dto) { return ApiResponse.ok(service.salvarTag(user.getUserId(), id, dto.nome(), dto.cor(), dto.ativo())); }
    @PostMapping("/campos") public ApiResponse<PipelineCampo> campo(@RequestBody CampoInput dto) { return salvarCampo(null, dto); }
    @PutMapping("/campos/{id}") public ApiResponse<PipelineCampo> salvarCampo(@PathVariable Long id, @RequestBody CampoInput dto) { return ApiResponse.ok(service.salvarCampo(user.getUserId(), id, dto.nome(), dto.tipo(), dto.escopo(), dto.opcoes(), dto.ativo())); }
    @GetMapping("/campanhas") public ApiResponse<List<PipelineCadastroService.CampanhaOpcao>> campanhas() { return ApiResponse.ok(service.campanhas(user.getUserId())); }

}
