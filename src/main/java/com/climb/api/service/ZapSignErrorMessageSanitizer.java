package com.climb.api.service;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

final class ZapSignErrorMessageSanitizer {
    private static final int MAX_LENGTH = 300;
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)\\\"(base64_pdf|base64_docx|cpf|email|name|external_id|token)\\\"\\s*:\\s*\\\"(?:\\\\.|[^\\\"])*\\\"");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern CPF = Pattern.compile(
            "(?<!\\d)\\d{3}[.\\s-]?\\d{3}[.\\s-]?\\d{3}[-\\s]?\\d{2}(?!\\d)");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern LONG_IDENTIFIER = Pattern.compile(
            "[A-Za-z0-9_+/=-]{32,}");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ZapSignErrorMessageSanitizer() {
    }

    static String sanitize(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }

        String sanitized = HTML_TAG.matcher(responseBody).replaceAll(" ");
        sanitized = SENSITIVE_JSON_FIELD.matcher(sanitized)
                .replaceAll("\"$1\":\"<omitido>\"");
        sanitized = EMAIL.matcher(sanitized).replaceAll("<email>");
        sanitized = CPF.matcher(sanitized).replaceAll("<cpf>");
        sanitized = AUTHORIZATION.matcher(sanitized).replaceAll("Bearer <token>");
        sanitized = LONG_IDENTIFIER.matcher(sanitized).replaceAll("<identificador>");
        sanitized = WHITESPACE.matcher(sanitized).replaceAll(" ").trim();

        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        return sanitized.length() <= MAX_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LENGTH) + "…";
    }
}
