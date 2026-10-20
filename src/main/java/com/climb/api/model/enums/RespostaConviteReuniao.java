package com.climb.api.model.enums;

public enum RespostaConviteReuniao {
    PENDENTE, ACEITA, RECUSADA, TENTATIVA;

    public static RespostaConviteReuniao fromGoogle(String status) {
        if (status == null) return PENDENTE;
        return switch (status) {
            case "accepted" -> ACEITA;
            case "declined" -> RECUSADA;
            case "tentative" -> TENTATIVA;
            default -> PENDENTE;
        };
    }

    public boolean definitiva() { return this == ACEITA || this == RECUSADA; }
}
