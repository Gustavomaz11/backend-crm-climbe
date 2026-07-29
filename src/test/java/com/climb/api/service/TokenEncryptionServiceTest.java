package com.climb.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokenEncryptionServiceTest {

    @Test
    void deveCriptografarEDescriptografarTokenGoogle() {
        TokenEncryptionService service = new TokenEncryptionService(
                "segredo-de-testes-com-pelo-menos-trinta-e-dois-bytes");

        String criptografado = service.criptografar("google-access-token");

        assertNotEquals("google-access-token", criptografado);
        assertEquals("google-access-token", service.descriptografar(criptografado));
    }
}
