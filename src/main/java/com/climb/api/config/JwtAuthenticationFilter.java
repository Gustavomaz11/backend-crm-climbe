package com.climb.api.config;

import com.climb.api.service.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        try {
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                final String token = authHeader.substring(BEARER_PREFIX.length());

                if (jwtUtil.validateToken(token)) {
                    String tokenType = jwtUtil.extractTokenType(token);
                    String email = jwtUtil.extractEmail(token);

                    if (JwtUtil.TYPE_ACCESS.equals(tokenType)) {
                        Long userId = jwtUtil.extractUserId(token);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        auth.setDetails(userId);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else if (JwtUtil.TYPE_PENDING_REGISTRATION.equals(tokenType)) {
                        Long pendingId = jwtUtil.extractPendingId(token);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                email, null, List.of(new SimpleGrantedAuthority("ROLE_PENDING_REGISTRATION")));
                        auth.setDetails(new PendingPrincipal(pendingId));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Falha ao processar JWT", e);
        }

        filterChain.doFilter(request, response);
    }
}
