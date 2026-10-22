package com.climb.api.integracao;

import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class IntegracaoSecurityConfig {
    @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain integracaoSecurityFilterChain(HttpSecurity http, IntegracaoService service,
            IntegracaoCatalogo catalogo, IntegracaoAuditoriaService auditoria, IntegracaoTentativas tentativas,
            @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.securityMatcher("/api/integracoes/**")
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/integracoes/oauth/token", "/api/integracoes/ativar").permitAll()
                        .anyRequest().hasRole("INTEGRATION"))
                .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(new IntegracaoAuthenticationFilter(service, catalogo, auditoria, tentativas), UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).build();
    }
}
