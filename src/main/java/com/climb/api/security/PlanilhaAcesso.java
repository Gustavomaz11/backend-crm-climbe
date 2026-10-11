package com.climb.api.security;

import com.climb.api.model.Planilha;
import com.climb.api.model.PermissaoCodigo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component("planilhaAcesso")
public class PlanilhaAcesso {
    private final PermissaoAcesso permissoes;

    public PlanilhaAcesso(PermissaoAcesso permissoes) { this.permissoes = permissoes; }

    public boolean tem(String acao) {
        return permissoes.tem("PLANILHA_" + acao)
                || (("LER".equals(acao) || "EDITAR".equals(acao))
                    && permissoes.tem("PLANILHA_EDICAO_RESTRITA"));
    }

    public boolean pode(String acao, Planilha planilha) {
        return tem(acao) && planilha.getContrato() != null
                && planilha.getContrato().getIdContrato() != null
                && permissoes.lerContrato(planilha.getContrato().getIdContrato())
                && podeVisualizar(planilha);
    }

    private boolean podeVisualizar(Planilha planilha) {
        String codigo = planilha.getPermissaoVisualizacao();
        if (codigo == null || codigo.isBlank()) return true;
        try {
            PermissaoCodigo.valueOf(codigo);
            return permissoes.tem(codigo);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void exigir(String acao, Planilha planilha) {
        if (!pode(acao, planilha)) throw new AccessDeniedException("Você não tem acesso a esta planilha.");
    }

    public void exigirConfiguracao(Planilha planilha) { exigir("CONFIGURAR_ACESSO", planilha); }
}
