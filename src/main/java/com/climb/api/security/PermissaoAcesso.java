package com.climb.api.security;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.model.enums.RevisaoDocumentoTipo;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.RevisaoDocumentoRepository;
import com.climb.api.service.ContratoEquipeService;
import com.climb.api.service.RbacService;
import org.springframework.stereotype.Component;

@Component("permissaoAcesso")
public class PermissaoAcesso {
    private final AuthenticatedUserProvider usuario;
    private final RbacService rbac;
    private final ContratoRepository contratos;
    private final ContratoEquipeService equipe;
    private final RevisaoDocumentoRepository revisoes;

    public PermissaoAcesso(AuthenticatedUserProvider usuario, RbacService rbac,
            ContratoRepository contratos, ContratoEquipeService equipe, RevisaoDocumentoRepository revisoes) {
        this.usuario = usuario;
        this.rbac = rbac;
        this.contratos = contratos;
        this.equipe = equipe;
        this.revisoes = revisoes;
    }

    public boolean tem(String codigo) {
        return rbac.temPermissao(usuario.getUserId(), PermissaoCodigo.valueOf(codigo));
    }

    public boolean proprioOuAdministrador(Long usuarioId) {
        return usuarioId != null && (usuarioId.equals(usuario.getUserId()) || tem("PERMITIR_ACESSO"));
    }

    public boolean contrato(String acao) {
        return ContratoPermissoes.permite(rbac, usuario.getUserId(),
                PermissaoCodigo.valueOf("JURIDICO_CONTRATO_" + acao));
    }

    public boolean criarContratoComUpload() {
        return tem("CONTRATO_CRIAR") || (contrato("CRIAR") && contrato("ENVIAR_CLIENTE"));
    }

    public boolean lerContrato(Long id) {
        if (contrato("LER")) return true;
        return contratos.findById(id).map(c -> equipe.podeVisualizar(c, usuario.getUserId())).orElse(false);
    }

    public boolean lerProposta(Long id) {
        if (tem("PROPOSTA_LER")) return true;
        // A equipe do contrato mantém acesso à proposta aprovada que faz parte dele.
        return contratos.findByProposta_IdProposta(id).map(c ->
                contrato("LER") || equipe.podeVisualizar(c, usuario.getUserId())).orElse(false);
    }

    public boolean lerDocumentoRevisao(RevisaoDocumentoTipo tipo, Long referenciaId) {
        return tipo == RevisaoDocumentoTipo.CONTRATO ? lerContrato(referenciaId) : lerProposta(referenciaId);
    }

    public boolean lerRevisao(Long id) {
        return revisoes.findById(id).map(r -> lerDocumentoRevisao(r.getTipo(), r.getReferenciaId())).orElse(false);
    }
}
