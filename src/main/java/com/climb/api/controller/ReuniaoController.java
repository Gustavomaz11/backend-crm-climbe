package com.climb.api.controller;

import com.climb.api.model.Reuniao;
import com.climb.api.model.dto.ReuniaoListItemDTO;
import com.climb.api.model.dto.ReuniaoRequestDTO;
import com.climb.api.service.ReuniaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public List<ReuniaoListItemDTO> listar(
            @RequestHeader(value = "X-Google-Access-Token", required = false) String googleAccessToken) {
        boolean comGoogle = googleAccessToken != null && !googleAccessToken.isBlank();
        log.info("GET /reunioes - header Google: presente={}, tamanho={}",
                comGoogle, comGoogle ? googleAccessToken.length() : 0);
        List<ReuniaoListItemDTO> out = service.listar(googleAccessToken);
        log.info("GET /reunioes - retornando {} itens", out.size());
        return out;
    }

    @GetMapping("/{id}")
    public Reuniao buscarPorId(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @GetMapping("/empresa/{empresaId}")
    public List<Reuniao> listarPorEmpresa(@PathVariable Long empresaId) {
        return service.listarPorEmpresa(empresaId);
    }

    @PostMapping
    public Reuniao criar(
            @RequestBody ReuniaoRequestDTO reuniao,
            @RequestHeader(value = "X-Google-Access-Token", required = false) String googleAccessToken) throws Exception {
        return service.criar(reuniao, googleAccessToken);
    }

    @PutMapping("/{id}")
    public Reuniao atualizar(
            @PathVariable Long id,
            @RequestBody ReuniaoRequestDTO atualizada,
            @RequestHeader(value = "X-Google-Access-Token", required = false) String googleAccessToken) {
        return service.atualizar(id, atualizada, googleAccessToken);
    }

    @DeleteMapping("/{id}")
    public void deletar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Google-Access-Token", required = false) String googleAccessToken) {
        service.deletar(id, googleAccessToken);
    }
}
