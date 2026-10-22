package com.climb.api.integracao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class IntegracaoEntregaService {
    public record Envio(IntegracaoCliente cliente, int versao, int tentativa) {}
    private final IntegracaoClienteRepository repository;

    @Transactional
    public Envio reservar() {
        var lista = repository.pendentes(Instant.now(), PageRequest.of(0, 1));
        if (lista.isEmpty()) return null;
        var c = lista.getFirst();
        if ("PENDENTE".equals(c.getStatus()) && (c.getAtivacaoExpiraEm() == null || !c.getAtivacaoExpiraEm().isAfter(Instant.now()))) {
            c.setEmailStatus("FALHOU"); c.setEmailProximaTentativa(null); return null;
        }
        c.setEmailTentativas(c.getEmailTentativas() + 1);
        c.setEmailProximaTentativa(Instant.now().plusSeconds(120));
        return new Envio(c, c.getEmailVersao(), c.getEmailTentativas());
    }
    @Transactional
    public void concluir(Envio envio, boolean enviado) {
        var c = repository.bloquear(envio.cliente().getId()).orElse(null);
        if (c == null || c.getEmailVersao() != envio.versao() || c.getEmailTentativas() != envio.tentativa()) return;
        c.setEmailStatus(enviado ? "ENVIADO" : "FALHOU");
        c.setEmailProximaTentativa(enviado || envio.tentativa() >= 8 ? null
                : Instant.now().plusSeconds(Math.min(3600L, 60L << Math.min(envio.tentativa() - 1, 6))));
    }
}
