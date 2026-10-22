package com.climb.api.integracao;

import com.climb.api.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class IntegracaoEmailProcessador {
    private static final Logger log = LoggerFactory.getLogger(IntegracaoEmailProcessador.class);
    private final IntegracaoEntregaService entrega;
    private final IntegracaoTokens tokens;
    private final IntegracaoPdfService pdf;
    private final EmailService email;
    @Value("${app.frontend-url:http://localhost:5173}") private String frontendUrl;
    @Value("${app.integracoes.email.enabled:true}") private boolean enabled;

    @Scheduled(fixedDelayString = "${app.integracoes.email.intervalo-ms:5000}",
            initialDelayString = "${app.integracoes.email.intervalo-ms:5000}", scheduler = "notificationEmailScheduler")
    public void processar() {
        if (!enabled) return;
        for (int i = 0; i < 5; i++) {
            var envio = entrega.reservar(); if (envio == null) return;
            boolean enviado = false;
            try {
                var c = envio.cliente();
                boolean ativar = "PENDENTE".equals(c.getStatus()) && c.getAtivacaoNonce() != null;
                String url = ativar ? frontendUrl.replaceAll("/+$", "") + "/integracoes/ativar#token=" + tokens.ativacao(c) : null;
                enviado = email.enviarEmailComPdf(c.getEmail(), "Climbe | Tokens e Integração | " + c.getNome(),
                        ativar ? "Ative sua integração" : "Documentação da integração",
                        "Olá, " + c.getResponsavel() + ".\nClient ID: " + c.getClientId() + "\nMódulo: " + c.getModulo()
                                + (ativar ? "\nUse o botão para obter seu client_secret. O link é de uso único e expira em até 24 horas."
                                : "\nA documentação atualizada está anexada. O segredo existente não é reenviado. Solicite a rotação caso o tenha perdido."),
                        ativar ? "Obter CLIENT_SECRET" : null, url, pdf.gerar(c));
            } catch (Exception e) {
                // Não registrar o corpo da mensagem ou o link de ativação.
                log.warn("Documentação da integração {} será reenviada; tentativa {}", envio.cliente().getId(), envio.tentativa());
            }
            entrega.concluir(envio, enviado);
        }
    }
}
