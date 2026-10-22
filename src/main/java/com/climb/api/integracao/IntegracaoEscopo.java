package com.climb.api.integracao;

import com.climb.api.model.*;
import com.climb.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.util.Set;

@Service @RequiredArgsConstructor
public class IntegracaoEscopo {
    private final ContratoRepository contratos;
    private final PropostaRepository propostas;
    private final EmpresaRepository empresas;
    private final PipelineVendasNegocioRepository negocios;

    public IntegracaoCliente cliente() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof IntegracaoCliente c))
            throw IntegracaoService.erro(HttpStatus.UNAUTHORIZED, "Integração não autenticada.");
        return c;
    }
    public Long ator() { return cliente().getCriadoPor(); }
    public Set<Long> empresas() { return cliente().getEmpresaIds(); }
    public boolean permite(Long empresaId) { return empresaId != null && empresas().contains(empresaId); }
    public Empresa empresa(Long id) {
        if (!permite(id)) throw indisponivel();
        return empresas.findById(id).orElseThrow(IntegracaoEscopo::indisponivel);
    }
    public Contrato contrato(Long id) {
        var c = contratos.findById(id).orElseThrow(IntegracaoEscopo::indisponivel);
        if (c.getEmpresa() == null || !permite(c.getEmpresa().getIdEmpresa())) throw indisponivel();
        return c;
    }
    public Proposta proposta(Long id) {
        var p = propostas.findById(id).orElseThrow(IntegracaoEscopo::indisponivel);
        if (p.getEmpresa() == null || !permite(p.getEmpresa().getIdEmpresa())) throw indisponivel();
        return p;
    }
    public PipelineVendasNegocio negocio(Long id) {
        var n = negocios.findById(id).orElseThrow(IntegracaoEscopo::indisponivel);
        if (n.getEmpresa() == null || !permite(n.getEmpresa().getIdEmpresa())) throw indisponivel();
        return n;
    }
    static org.springframework.web.server.ResponseStatusException indisponivel() {
        return IntegracaoService.erro(HttpStatus.NOT_FOUND, "Registro não encontrado no escopo autorizado.");
    }
}
