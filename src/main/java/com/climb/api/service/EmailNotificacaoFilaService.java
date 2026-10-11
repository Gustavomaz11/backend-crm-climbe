package com.climb.api.service;

import com.climb.api.model.EmailNotificacaoPendente;
import com.climb.api.repository.EmailNotificacaoPendenteRepository;
import com.climb.api.service.NotificacaoEntregaService.EmailNotificacao;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Persiste junto da operação; reserva o envio em uma transação curta e separada do SMTP. */
@Service
public class EmailNotificacaoFilaService {
    private final EmailNotificacaoPendenteRepository repository;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    public EmailNotificacaoFilaService(EmailNotificacaoPendenteRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void enfileirar(EmailNotificacao email) {
        if (email.destinatario() == null || email.destinatario().isBlank()) return;
        repository.save(new EmailNotificacaoPendente(email.destinatario(), email.assunto(), email.mensagem(),
                email.destino(), LocalDateTime.now(clock)));
    }

    public record Envio(Long id, int tentativa, EmailNotificacao email) {}

    public Optional<Envio> reservar() {
        return transaction.execute(status -> {
            LocalDateTime agora = LocalDateTime.now(clock);
            var pendentes = repository.buscarParaEnviar(agora, PageRequest.of(0, 1));
            if (pendentes.isEmpty()) return Optional.empty();
            var pendente = pendentes.getFirst();
            pendente.setTentativas(pendente.getTentativas() + 1);
            // Uma instância que cair durante o envio libera o e-mail para outra tentativa.
            pendente.setProximaTentativa(agora.plusMinutes(10));
            return Optional.of(new Envio(pendente.getId(), pendente.getTentativas(), new EmailNotificacao(
                    pendente.getDestinatario(), pendente.getAssunto(), pendente.getMensagem(), pendente.getDestino())));
        });
    }

    public void confirmar(Envio envio) {
        transaction.executeWithoutResult(status -> repository.confirmarEnvio(envio.id(), envio.tentativa()));
    }

    public void reagendar(Envio envio) {
        long segundos = Math.min(3600L, 60L << Math.min(envio.tentativa() - 1, 6));
        transaction.executeWithoutResult(status -> repository.reagendar(envio.id(), envio.tentativa(),
                LocalDateTime.now(clock).plusSeconds(segundos)));
    }
}
