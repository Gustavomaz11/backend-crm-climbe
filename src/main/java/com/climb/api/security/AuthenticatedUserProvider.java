package com.climb.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserProvider {
    public Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }

        Object details = authentication.getDetails();
        if (details instanceof Long userId) return userId;
        if (details instanceof Integer userId) return userId.longValue();
        if (details instanceof String userId) return Long.parseLong(userId);
        throw new IllegalStateException("Usuário não autenticado");
    }
}
