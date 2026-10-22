package com.climb.api.integracao;

import java.util.Locale;

/** Módulo 11 numérico/alfanumérico: https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf */
final class IntegracaoCnpj {
    private IntegracaoCnpj() {}
    static boolean valido(String valor) {
        if (valor == null) return false;
        String cnpj = valor.replaceAll("[./-]", "").toUpperCase(Locale.ROOT);
        if (!cnpj.matches("[A-Z0-9]{12}[0-9]{2}") || cnpj.matches("([0-9])\\1{13}")) return false;
        return digito(cnpj.substring(0, 12)) == cnpj.charAt(12) - '0'
                && digito(cnpj.substring(0, 13)) == cnpj.charAt(13) - '0';
    }
    private static int digito(String base) {
        int soma = 0, peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - 48) * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
