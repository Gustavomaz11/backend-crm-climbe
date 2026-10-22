package com.climb.api.integracao;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class IntegracaoAuthenticationFilter extends OncePerRequestFilter {
    private final IntegracaoService service;
    private final IntegracaoCatalogo catalogo;
    private final IntegracaoAuditoriaService auditoria;
    private final IntegracaoTentativas tentativas;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if ("OPTIONS".equals(request.getMethod())) { chain.doFilter(request, response); return; }
        if (caminho.equals("/api/integracoes/oauth/token") || caminho.equals("/api/integracoes/ativar")) {
            try { tentativas.verificar(request.getRemoteAddr()); }
            catch (ResponseStatusException e) { responder(response, e); return; }
            chain.doFilter(request, response); return;
        }
        IntegracaoCliente cliente;
        IntegracaoCatalogo.Operacao operacao;
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ") || auth.length() > 4096)
                throw IntegracaoService.erro(org.springframework.http.HttpStatus.UNAUTHORIZED, "Informe um access_token de integração.");
            cliente = service.validarAcesso(auth.substring(7));
            operacao = catalogo.localizar(request.getMethod(), caminho).orElse(null);
        } catch (ResponseStatusException e) { responder(response, e); return; }
        int status = 500;
        var requisicao = new ContentCachingRequestWrapper(request, IntegracaoPayloadAuditoria.LIMITE_BYTES + 1);
        try {
            if (operacao == null || operacao.modulo() != cliente.getModulo()
                    || !cliente.getMetodos().contains(request.getMethod()) || !cliente.getOperacoes().contains(operacao.codigo())) {
                response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Operação não autorizada para esta integração.\"}");
            } else {
                var auth = new UsernamePasswordAuthenticationToken(cliente, null, List.of(new SimpleGrantedAuthority("ROLE_INTEGRATION")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                chain.doFilter(requisicao, response);
            }
            status = response.getStatus();
        } finally {
            SecurityContextHolder.clearContext();
            var captura = IntegracaoPayloadAuditoria.capturar(requisicao, status);
            auditoria.registrar(cliente.getId(), "REQUISICAO", null, request.getMethod(),
                    operacao == null ? "/api/integracoes/operacao-nao-catalogada" : operacao.caminho(), status,
                    captura.payload(), captura.limiteExcedido());
        }
    }
    private void responder(HttpServletResponse response, ResponseStatusException e) throws IOException {
        response.setStatus(e.getStatusCode().value()); response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (e.getStatusCode().value() == 401) response.setHeader("WWW-Authenticate", "Bearer");
        if (e.getStatusCode().value() == 429) response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"message\":\"Acesso inválido, expirado ou temporariamente limitado.\"}");
    }
}
