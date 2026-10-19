package com.climb.api.controller;

import jakarta.validation.Valid;
import com.climb.api.model.dto.KanbanArquivamentoRequestDTO;
import com.climb.api.model.dto.KanbanItemArquivadoDTO;
import com.climb.api.model.Reuniao;
import com.climb.api.model.dto.ReuniaoListItemDTO;
import com.climb.api.model.dto.ReuniaoRequestDTO;
import com.climb.api.model.dto.SalaReuniaoDTO;
import com.climb.api.model.dto.ReuniaoDisponibilidadeDTO;
import com.climb.api.model.dto.ReuniaoDisponibilidadeRequestDTO;
import com.climb.api.service.ReuniaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reunioes")
public class ReuniaoController {

    private static final Logger log = LoggerFactory.getLogger(ReuniaoController.class);

    private final ReuniaoService service;

    public ReuniaoController(ReuniaoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReuniaoListItemDTO> listar() {
        List<ReuniaoListItemDTO> out = service.listar(getAuthenticatedUserId());
        log.info("GET /reunioes - retornando {} itens", out.size());
        return out;
    }

    @GetMapping("/{id}")
    public Reuniao buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id, getAuthenticatedUserId());
    }

    @GetMapping("/salas")
    public List<SalaReuniaoDTO> listarSalas() {
        return service.listarSalas(getAuthenticatedUserId());
    }

    @PostMapping("/disponibilidade")
    public ReuniaoDisponibilidadeDTO consultarDisponibilidade(@Valid @RequestBody ReuniaoDisponibilidadeRequestDTO request) {
        return service.consultarDisponibilidade(request, getAuthenticatedUserId());
    }

    @GetMapping("/{id}/detalhes")
    public ReuniaoListItemDTO buscarDetalhes(@PathVariable Long id) {
        return service.buscarDetalhes(id, getAuthenticatedUserId());
    }

    @GetMapping("/empresa/{empresaId}")
    public List<Reuniao> listarPorEmpresa(@PathVariable Long empresaId) {
        return service.listarPorEmpresa(empresaId, getAuthenticatedUserId());
    }

    @PostMapping
    public Reuniao criar(@RequestBody ReuniaoRequestDTO reuniao) throws Exception {
        return service.criar(reuniao, getAuthenticatedUserId());
    }

    @PutMapping("/{id}")
    public Reuniao atualizar(
            @PathVariable Long id,
            @RequestBody ReuniaoRequestDTO atualizada) {
        return service.atualizar(id, getAuthenticatedUserId(), atualizada);
    }

    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        service.deletar(id, getAuthenticatedUserId());
    }

    @GetMapping("/arquivados")
    public List<KanbanItemArquivadoDTO> listarArquivados() {
        return service.listarArquivados(getAuthenticatedUserId());
    }

    @PatchMapping("/{id}/arquivamento")
    public void alterarArquivamento(@PathVariable Long id,
            @Valid @RequestBody KanbanArquivamentoRequestDTO dto) {
        service.alterarArquivamento(id, getAuthenticatedUserId(), dto.arquivado());
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }

        Object details = auth.getDetails();
        if (details instanceof Long userId) {
            return userId;
        }
        if (details instanceof Integer userId) {
            return userId.longValue();
        }
        if (details instanceof String userId) {
            return Long.parseLong(userId);
        }

        throw new IllegalStateException("Usuário não autenticado");
    }
}
