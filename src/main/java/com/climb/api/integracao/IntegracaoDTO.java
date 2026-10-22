package com.climb.api.integracao;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;

public final class IntegracaoDTO {
    private IntegracaoDTO() {}
    public record Cadastro(
            @NotBlank @Size(max = 180) String nome,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9./-]{14,18}") String cnpj,
            @NotBlank @Size(max = 180) String responsavel,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull IntegracaoModulo modulo,
            @NotEmpty @Size(max = 5) Set<@NotBlank String> metodos,
            @NotEmpty @Size(max = 200) Set<@NotBlank String> operacoes,
            @Size(max = 500) Set<@NotNull @Positive Long> empresaIds,
            @NotNull @Future Instant expiraEm,
            @Min(1) @Max(3000) int limitePorMinuto) {
        public Cadastro { empresaIds = empresaIds == null ? Set.of() : empresaIds; }
    }

    public record Resumo(String id, String clientId, String nome, String cnpj, String responsavel,
            String email, IntegracaoModulo modulo, String status, Set<String> metodos, Set<String> operacoes,
            Set<Long> empresaIds, Instant criadoEm, Instant expiraEm, Instant ultimoUso, String emailStatus,
            int emailTentativas, int limitePorMinuto) {
        public static Resumo de(IntegracaoCliente c) {
            return new Resumo(c.getId(), c.getClientId(), c.getNome(), c.getCnpj(), c.getResponsavel(),
                    c.getEmail(), c.getModulo(), c.getStatus(), Set.copyOf(c.getMetodos()), Set.copyOf(c.getOperacoes()),
                    Set.copyOf(c.getEmpresaIds()), c.getCriadoEm(), c.getExpiraEm(), c.getUltimoUso(),
                    c.getEmailStatus(), c.getEmailTentativas(), c.getLimitePorMinuto());
        }
    }
    public record Ativacao(@NotBlank @Size(max = 2048) String token) {}
    public record Credenciais(String client_id, String client_secret) {}
    public record Token(String access_token, String token_type, long expires_in, String scope) {}
    public record AuditoriaResumo(Long id, String evento, Long atorId, String metodo, String rota, Integer status,
                                  Instant criadoEm, boolean payloadDisponivel, boolean payloadLimiteExcedido) {
        static AuditoriaResumo de(IntegracaoAuditoria a) {
            return new AuditoriaResumo(a.getId(), a.getEvento(), a.getAtorId(), a.getMetodo(), a.getRota(), a.getStatus(),
                    a.getCriadoEm(), a.getPayloadRequisicao() != null, a.isPayloadLimiteExcedido());
        }
    }
    public record PayloadAuditoria(String payload) {}
    public record AlterarStatus(@Pattern(regexp = "SUSPENSO|ATIVO|REVOGADO") @NotNull String status) {}
}
