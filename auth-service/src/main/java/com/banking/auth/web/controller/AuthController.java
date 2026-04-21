package com.banking.auth.web.controller;

import com.banking.auth.domain.User;
import com.banking.auth.service.AuthService;
import com.banking.auth.web.api.AuthApi;
import com.banking.auth.web.dto.*;
import com.banking.common.AppConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<AuthResponse> register(
            RegisterRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = AppConstants.Headers.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, httpRequest.getRemoteAddr(), idempotencyKey));
    }

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @Override
    public ResponseEntity<AuthResponse> refresh(RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Override
    public ResponseEntity<Void> logout(User user) {
        authService.logout(user.getId());
        return ResponseEntity.noContent().build();
    }
}
