package com.climb.api.security;

import com.climb.api.model.PermissaoCodigo;
import com.climb.api.service.RbacService;

public final class ContratoPermissoes {
    private ContratoPermissoes() {}

    public static boolean permite(RbacService rbac, Long usuarioId, PermissaoCodigo juridico) {
        if (usuarioId == null) return false;
        PermissaoCodigo anterior = switch (juridico) {
            case JURIDICO_CONTRATO_LER -> PermissaoCodigo.CONTRATO_LER;
            case JURIDICO_CONTRATO_CRIAR -> PermissaoCodigo.CONTRATO_CRIAR;
            case JURIDICO_CONTRATO_DELETAR -> PermissaoCodigo.CONTRATO_DELETAR;
            case JURIDICO_CONTRATO_EDITAR, JURIDICO_CONTRATO_MOVIMENTAR,
                    JURIDICO_CONTRATO_ANEXAR_ARQUIVO, JURIDICO_CONTRATO_ENVIAR_CLIENTE,
                    JURIDICO_CONTRATO_ENVIAR_NOVA_VERSAO, JURIDICO_CONTRATO_REENVIAR,
                    JURIDICO_CONTRATO_RESPONSAVEIS_EDITAR, JURIDICO_CONTRATO_VENCIMENTOS_EDITAR,
                    JURIDICO_CONTRATO_APROVAR, JURIDICO_CONTRATO_DESVINCULAR_PROPOSTA,
                    JURIDICO_CONTRATO_ARQUIVAR, JURIDICO_CONTRATO_DESARQUIVAR -> PermissaoCodigo.CONTRATO_EDITAR;
            default -> throw new IllegalArgumentException("Permissão não pertence ao Jurídico de contratos");
        };
        return rbac.temPermissao(usuarioId, anterior) || rbac.temPermissao(usuarioId, juridico);
    }
}
