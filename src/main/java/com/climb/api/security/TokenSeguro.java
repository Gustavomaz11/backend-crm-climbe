package com.climb.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenSeguro {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokenSeguro() {}
    public static String aleatorio() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static byte[] sha256(String valor) {
        try { return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String hash(String valor) { return HexFormat.of().formatHex(sha256(valor)); }
    public static String challenge(String verifier) { return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier)); }
    public static boolean iguais(String esperado, String recebido) {
        return esperado != null && recebido != null && MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recebido.getBytes(StandardCharsets.UTF_8));
    }
}
