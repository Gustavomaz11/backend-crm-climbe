package com.climb.api.integracao;

import org.springframework.data.domain.*;
import java.util.List;

public record IntegracaoPagina<T>(List<T> itens, int pagina, int tamanho, long total, int paginas) {
    public static Pageable pedido(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw IntegracaoService.erro(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Use pagina >= 0 e tamanho entre 1 e 100.");
        return PageRequest.of(pagina, tamanho);
    }
    public static <T> IntegracaoPagina<T> de(Page<T> p) {
        return new IntegracaoPagina<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
