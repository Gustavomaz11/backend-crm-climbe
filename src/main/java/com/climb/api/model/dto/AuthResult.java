package com.climb.api.model.dto;

import com.climb.api.model.AuthStatus;

public record AuthResult<T>(AuthStatus status, T data, String message) {

    public boolean isSuccess() {
        return status == AuthStatus.SUCCESS;
    }

    public static <T> AuthResult<T> success(T data) {
        return new AuthResult<>(AuthStatus.SUCCESS, data, null);
    }

    public static <T> AuthResult<T> failure(AuthStatus status, String message) {
        return new AuthResult<>(status, null, message);
    }
}
