package com.climb.api.integracao;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;

/** Proteção de entrada por instância; o limite das integrações é persistido e compartilhado. */
@Component
public class IntegracaoTentativas {
    private record Janela(long minuto, int quantidade) {}
    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    public synchronized void verificar(String endereco) {
        long minuto = java.time.Instant.now().getEpochSecond() / 60;
        if (janelas.size() > 5000) janelas.entrySet().removeIf(e -> e.getValue().minuto() < minuto);
        if (janelas.size() >= 10000 && !janelas.containsKey(endereco)) negar();
        var atual = janelas.get(endereco);
        int quantidade = atual != null && atual.minuto() == minuto ? atual.quantidade() + 1 : 1;
        if (quantidade > 30) negar();
        janelas.put(endereco, new Janela(minuto, quantidade));
    }
    private void negar() { throw IntegracaoService.erro(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde um minuto."); }
}
