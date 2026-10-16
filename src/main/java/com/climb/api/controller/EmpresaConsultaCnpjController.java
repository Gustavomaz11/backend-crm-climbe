package com.climb.api.controller;

import com.climb.api.model.dto.CnpjEmpresaResponseDTO;
import com.climb.api.service.CnpjaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empresas/consulta-cnpj")
public class EmpresaConsultaCnpjController {
    private final CnpjaService cnpja;

    public EmpresaConsultaCnpjController(CnpjaService cnpja) {
        this.cnpja = cnpja;
    }

    @GetMapping("/{cnpj}")
    public ResponseEntity<CnpjEmpresaResponseDTO> consultar(@PathVariable String cnpj) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cnpja.consultar(cnpj));
    }
}
