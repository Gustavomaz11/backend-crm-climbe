package com.climb.api.service;

import com.climb.api.model.OAuthLoginRequest;
import com.climb.api.repository.OAuthLoginRequestRepository;
import com.climb.api.security.TokenSeguro;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
public class OAuthLoginRequestService {
    private final OAuthLoginRequestRepository requests;
    public OAuthLoginRequestService(OAuthLoginRequestRepository requests) { this.requests = requests; }
    public record Inicio(String state, String browserBinding, String googleChallenge) {}
    public record Validado(String codeVerifier, String appChallenge) {}

    @Transactional
    public Inicio iniciar(String appChallenge) {
        if (appChallenge == null || !appChallenge.matches("[A-Za-z0-9_-]{43}")) throw invalido();
        String state = TokenSeguro.aleatorio(), binding = TokenSeguro.aleatorio(), verifier = TokenSeguro.aleatorio();
        var request = new OAuthLoginRequest();
        request.setStateHash(TokenSeguro.hash(state)); request.setBrowserHash(TokenSeguro.hash(binding));
        request.setCodeVerifier(verifier); request.setAppChallenge(appChallenge);
        request.setExpiraEm(Instant.now().plusSeconds(600));
        requests.deleteByExpiraEmBefore(Instant.now()); requests.save(request);
        return new Inicio(state, binding, TokenSeguro.challenge(verifier));
    }

    @Transactional
    public Validado consumir(String state, String binding) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}") || binding == null || !binding.matches("[A-Za-z0-9_-]{43}")) throw invalido();
        var request = requests.buscarParaConsumir(TokenSeguro.hash(state)).orElseThrow(OAuthLoginRequestService::invalido);
        if (!request.getExpiraEm().isAfter(Instant.now()) || !TokenSeguro.iguais(request.getBrowserHash(), TokenSeguro.hash(binding))) throw invalido();
        requests.delete(request); requests.flush();
        return new Validado(request.getCodeVerifier(), request.getAppChallenge());
    }
    private static ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "A autenticação expirou ou não foi iniciada neste navegador. Tente novamente.");
    }
}
