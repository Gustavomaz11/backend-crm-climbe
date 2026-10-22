package com.climb.api.integracao;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.http.server.PathContainer;
import java.util.*;

@Service
public class IntegracaoCatalogo {
    public record Operacao(String codigo, IntegracaoModulo modulo, String metodo, String caminho,
                          String descricao, String payloadExemplo, String respostaExemplo) {}
    private final List<Operacao> operacoes;
    public IntegracaoCatalogo() {
        var items = new ArrayList<Operacao>();
        for (var tipo : List.of(IntegracaoTecnicoController.class, IntegracaoComercialController.class,
                IntegracaoJuridicoController.class)) {
            var base = tipo.getAnnotation(RequestMapping.class).value()[0];
            for (var metodo : tipo.getDeclaredMethods()) {
                var item = metodo.getAnnotation(IntegracaoEndpoint.class);
                if (item == null) continue;
                var rota = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
                if (rota == null || rota.method().length != 1) throw new IllegalStateException("Operação sem rota única");
                var caminho = base + (rota.path().length == 0 ? "" : rota.path()[0]);
                items.add(new Operacao(item.codigo(), item.modulo(), rota.method()[0].name(), caminho,
                        item.descricao(), item.exemplo(), item.resposta()));
            }
        }
        if (items.stream().map(Operacao::codigo).distinct().count() != items.size())
            throw new IllegalStateException("Códigos de integração duplicados");
        operacoes = items.stream().sorted(Comparator.comparing(Operacao::codigo)).toList();
    }
    public List<Operacao> listar(IntegracaoModulo modulo) {
        return operacoes.stream().filter(o -> modulo == null || o.modulo() == modulo).toList();
    }
    public List<Operacao> permitidas(IntegracaoCliente c) {
        return listar(c.getModulo()).stream().filter(o -> c.getMetodos().contains(o.metodo())
                && c.getOperacoes().contains(o.codigo())).toList();
    }
    public Optional<Operacao> localizar(String metodo, String caminho) {
        return operacoes.stream().filter(o -> o.metodo().equals(metodo)
                && PathPatternParser.defaultInstance.parse(o.caminho()).matches(PathContainer.parsePath(caminho))).findFirst();
    }
}
