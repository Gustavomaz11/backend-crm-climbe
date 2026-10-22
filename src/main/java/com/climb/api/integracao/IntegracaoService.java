package com.climb.api.integracao;

import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.security.TokenSeguro;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class IntegracaoService {
    private static final Set<String> METODOS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private final IntegracaoClienteRepository repository;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final IntegracaoCatalogo catalogo;
    private final IntegracaoTokens tokens;
    private final IntegracaoAuditoriaRepository auditoria;

    @Transactional
    public IntegracaoDTO.Resumo salvar(String id, IntegracaoDTO.Cadastro dto, Long ator) {
        validar(dto);
        var c = id == null ? new IntegracaoCliente() : bloquear(id);
        if ("REVOGADO".equals(c.getStatus())) throw erro(HttpStatus.CONFLICT, "Esta integração foi revogada.");
        if (id == null) {
            c.setId(UUID.randomUUID().toString()); c.setClientId("climbe_" + UUID.randomUUID().toString().replace("-", ""));
            c.setCriadoPor(ator);
        } else {
            c.setVersao(c.getVersao() + 1);
        }
        boolean novoEmail = id != null && !dto.email().trim().equalsIgnoreCase(c.getEmail());
        c.setNome(dto.nome().trim()); c.setCnpj(dto.cnpj().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT));
        c.setResponsavel(dto.responsavel().trim()); c.setEmail(dto.email().trim().toLowerCase(Locale.ROOT));
        c.setModulo(dto.modulo()); c.setMetodos(new LinkedHashSet<>(dto.metodos()));
        c.setOperacoes(new LinkedHashSet<>(dto.operacoes()));
        c.setEmpresaIds(dto.metodos().contains("GET") ? new LinkedHashSet<>(dto.empresaIds()) : new LinkedHashSet<>());
        c.setExpiraEm(dto.expiraEm()); c.setLimitePorMinuto(dto.limitePorMinuto());
        if (id == null || novoEmail || "PENDENTE".equals(c.getStatus())) prepararAtivacao(c);
        enfileirar(c); repository.save(c); registrar(c, id == null ? "CRIADA" : "ATUALIZADA", ator);
        return IntegracaoDTO.Resumo.de(c);
    }
    private void validar(IntegracaoDTO.Cadastro dto) {
        if (!METODOS.containsAll(dto.metodos()) || !dto.expiraEm().isAfter(Instant.now()))
            throw erro(HttpStatus.BAD_REQUEST, "Métodos ou validade inválidos.");
        if (!IntegracaoCnpj.valido(dto.cnpj())) throw erro(HttpStatus.BAD_REQUEST, "Informe um CNPJ válido, com os dígitos verificadores corretos.");
        if (dto.metodos().contains("GET")) {
            if (dto.empresaIds().isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Selecione ao menos uma empresa autorizada para GET.");
            if (empresas.countByIdEmpresaIn(dto.empresaIds()) != dto.empresaIds().size())
                throw erro(HttpStatus.BAD_REQUEST, "Uma das empresas selecionadas não existe.");
        }
        var disponiveis = catalogo.listar(dto.modulo()).stream()
                .filter(o -> dto.metodos().contains(o.metodo())).map(IntegracaoCatalogo.Operacao::codigo).toList();
        if (!disponiveis.containsAll(dto.operacoes())) throw erro(HttpStatus.BAD_REQUEST, "Operação incompatível com o módulo ou métodos.");
        for (var metodo : dto.metodos()) {
            if (catalogo.listar(dto.modulo()).stream().noneMatch(o -> metodo.equals(o.metodo()) && dto.operacoes().contains(o.codigo())))
                throw erro(HttpStatus.BAD_REQUEST, "Selecione ao menos uma operação para o método " + metodo + ".");
        }
    }
    @Transactional
    public IntegracaoDTO.Credenciais ativar(String token) {
        var claims = tokens.validar(token, "ativacao");
        var c = bloquear(claims.getSubject());
        if (!"PENDENTE".equals(c.getStatus()) || c.getAtivacaoExpiraEm() == null
                || !c.getAtivacaoExpiraEm().isAfter(Instant.now()) || !c.getExpiraEm().isAfter(Instant.now())
                || !TokenSeguro.iguais(c.getAtivacaoNonce(), claims.getId())
                || c.getVersao() != claims.get("versao", Integer.class)) throw erro(HttpStatus.UNAUTHORIZED, "Link inválido, utilizado ou expirado.");
        String segredo = TokenSeguro.aleatorio();
        c.setSecretHash(TokenSeguro.hash(segredo)); c.setStatus("ATIVO"); c.setAtivacaoNonce(null); c.setAtivacaoExpiraEm(null);
        registrar(c, "ATIVADA", null);
        return new IntegracaoDTO.Credenciais(c.getClientId(), segredo);
    }
    @Transactional
    public IntegracaoDTO.Token autenticar(String clientId, String segredo) {
        if (clientId == null || segredo == null || clientId.length() > 80 || segredo.length() > 256)
            throw erro(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        var c = repository.findByClientId(clientId).orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));
        exigirAtiva(c);
        if (!TokenSeguro.iguais(c.getSecretHash(), TokenSeguro.hash(segredo))) throw erro(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        consumir(c);
        registrar(c, "TOKEN_EMITIDO", null);
        return new IntegracaoDTO.Token(tokens.acesso(c), "Bearer", Math.min(900, Math.max(1, c.getExpiraEm().getEpochSecond() - Instant.now().getEpochSecond())),
                String.join(" ", new TreeSet<>(c.getOperacoes())));
    }
    @Transactional
    public IntegracaoCliente validarAcesso(String token) {
        var claims = tokens.validar(token, "acesso");
        var c = bloquear(claims.getSubject()); exigirAtiva(c);
        if (c.getVersao() != claims.get("versao", Integer.class)) throw erro(HttpStatus.UNAUTHORIZED, "Token revogado.");
        consumir(c); c.setUltimoUso(Instant.now());
        return c;
    }
    private void exigirAtiva(IntegracaoCliente c) {
        if (!"ATIVO".equals(c.getStatus()) || !c.getExpiraEm().isAfter(Instant.now())
                || usuarios.findById(c.getCriadoPor()).filter(u -> "ATIVO".equals(u.getSituacao())).isEmpty())
            throw erro(HttpStatus.UNAUTHORIZED, "Integração inativa ou expirada.");
    }
    private void consumir(IntegracaoCliente c) {
        long minuto = Instant.now().getEpochSecond() / 60;
        if (c.getJanelaMinuto() != minuto) { c.setJanelaMinuto(minuto); c.setRequisicoesJanela(0); }
        if (c.getRequisicoesJanela() >= c.getLimitePorMinuto()) throw erro(HttpStatus.TOO_MANY_REQUESTS, "Limite de requisições atingido. Tente no próximo minuto.");
        c.setRequisicoesJanela(c.getRequisicoesJanela() + 1);
    }
    @Transactional
    public IntegracaoDTO.Resumo alterarStatus(String id, String status, Long ator) {
        var c = bloquear(id);
        if ("REVOGADO".equals(c.getStatus())
                || ("SUSPENSO".equals(status) && !"ATIVO".equals(c.getStatus()))
                || ("ATIVO".equals(status) && (!"SUSPENSO".equals(c.getStatus()) || c.getSecretHash() == null || !c.getExpiraEm().isAfter(Instant.now()))))
            throw erro(HttpStatus.CONFLICT, "Esta integração não pode ser reativada. Gere novas credenciais quando aplicável.");
        c.setVersao(c.getVersao() + 1); c.setStatus(status);
        if ("REVOGADO".equals(status)) {
            c.setSecretHash(null); c.setAtivacaoNonce(null); c.setAtivacaoExpiraEm(null); c.setEmailProximaTentativa(null);
        }
        registrar(c, status, ator); return IntegracaoDTO.Resumo.de(c);
    }
    @Transactional
    public IntegracaoDTO.Resumo rotacionar(String id, Long ator) {
        var c = bloquear(id);
        if ("REVOGADO".equals(c.getStatus()) || !c.getExpiraEm().isAfter(Instant.now())) throw erro(HttpStatus.CONFLICT, "Integração revogada ou expirada.");
        c.setVersao(c.getVersao() + 1); prepararAtivacao(c); enfileirar(c); registrar(c, "CREDENCIAIS_ROTACIONADAS", ator);
        return IntegracaoDTO.Resumo.de(c);
    }
    @Transactional
    public void reenviar(String id, Long ator) {
        var c = bloquear(id);
        if ("REVOGADO".equals(c.getStatus())) throw erro(HttpStatus.CONFLICT, "Integração revogada.");
        if ("PENDENTE".equals(c.getStatus()) && (c.getAtivacaoExpiraEm() == null || !c.getAtivacaoExpiraEm().isAfter(Instant.now()))) prepararAtivacao(c);
        enfileirar(c); registrar(c, "EMAIL_SOLICITADO", ator);
    }
    private void prepararAtivacao(IntegracaoCliente c) {
        c.setStatus("PENDENTE"); c.setSecretHash(null); c.setAtivacaoNonce(TokenSeguro.aleatorio());
        c.setAtivacaoExpiraEm(Instant.now().plusSeconds(86400));
    }
    private void enfileirar(IntegracaoCliente c) {
        c.setEmailStatus("PENDENTE"); c.setEmailTentativas(0); c.setEmailVersao(c.getEmailVersao() + 1);
        c.setEmailProximaTentativa(Instant.now());
    }
    public IntegracaoCliente buscar(String id) { return repository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Integração não encontrada.")); }
    private IntegracaoCliente bloquear(String id) { return repository.bloquear(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Integração não encontrada.")); }
    private void registrar(IntegracaoCliente c, String evento, Long ator) {
        var a = new IntegracaoAuditoria(); a.setIntegracaoId(c.getId()); a.setEvento(evento); a.setAtorId(ator); auditoria.save(a);
    }
    static ResponseStatusException erro(HttpStatus status, String mensagem) { return new ResponseStatusException(status, mensagem); }
}
