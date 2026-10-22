package com.climb.api.integracao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity @Table(name = "integracao_clientes") @Getter @Setter
public class IntegracaoCliente {
    @Id @Column(length = 36) private String id;
    @Column(name = "client_id", nullable = false, unique = true, length = 80) private String clientId;
    @Column(name = "secret_hash", length = 64) private String secretHash;
    @Column(nullable = false, length = 180) private String nome;
    @Column(nullable = false, length = 14) private String cnpj;
    @Column(nullable = false, length = 180) private String responsavel;
    @Column(nullable = false, length = 254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IntegracaoModulo modulo;
    @Column(nullable = false, length = 20) private String status = "PENDENTE";
    @Column(name = "criado_por", nullable = false) private Long criadoPor;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm = Instant.now();
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
    @Column(name = "ultimo_uso") private Instant ultimoUso;
    @Column(nullable = false) private int versao = 1;
    @Column(name = "ativacao_nonce", length = 64) private String ativacaoNonce;
    @Column(name = "ativacao_expira_em") private Instant ativacaoExpiraEm;
    @Column(name = "email_status", nullable = false, length = 20) private String emailStatus = "PENDENTE";
    @Column(name = "email_tentativas", nullable = false) private int emailTentativas;
    @Column(name = "email_proxima_tentativa") private Instant emailProximaTentativa;
    @Column(name = "email_versao", nullable = false) private int emailVersao;
    @Column(name = "limite_por_minuto", nullable = false) private int limitePorMinuto = 120;
    @Column(name = "janela_minuto", nullable = false) private long janelaMinuto;
    @Column(name = "requisicoes_janela", nullable = false) private int requisicoesJanela;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "integracao_cliente_empresas", joinColumns = @JoinColumn(name = "integracao_id"))
    @Column(name = "empresa_id") private Set<Long> empresaIds = new LinkedHashSet<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "integracao_cliente_metodos", joinColumns = @JoinColumn(name = "integracao_id"))
    @Column(name = "metodo", length = 10) private Set<String> metodos = new LinkedHashSet<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "integracao_cliente_operacoes", joinColumns = @JoinColumn(name = "integracao_id"))
    @Column(name = "operacao", length = 100) private Set<String> operacoes = new LinkedHashSet<>();
}
