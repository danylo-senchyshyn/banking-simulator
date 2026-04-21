package com.banking.auth.web.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {}
