package com.banking.auth.web.controller;

import com.banking.auth.exception.AuthException;
import com.banking.auth.security.JwtAuthenticationFilter;
import com.banking.auth.security.JwtProperties;
import com.banking.auth.security.JwtService;
import com.banking.auth.service.AuthService;
import com.banking.auth.web.dto.AuthResponse;
import com.banking.auth.web.dto.LoginRequest;
import com.banking.auth.web.dto.RefreshRequest;
import com.banking.auth.web.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthService authService;

    // Security-related beans that would be injected into the filter/config
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    JwtService jwtService;

    @MockBean
    JwtProperties jwtProperties;

    @MockBean(name = "userDetailsService")
    UserDetailsService userDetailsService;

    private static final AuthResponse SAMPLE_RESPONSE = new AuthResponse("access.token.jwt", "refresh-token-uuid");

    // =====================================================================
    // POST /api/v1/auth/register
    // =====================================================================

    @Test
    void register_validRequest_returns201WithTokens() throws Exception {
        when(authService.register(any(RegisterRequest.class), anyString(), any()))
                .thenReturn(SAMPLE_RESPONSE);

        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John", "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-uuid"));
    }

    @Test
    void register_missingEmail_returns400() throws Exception {
        // email is null — violates @NotBlank @Email
        RegisterRequest request = new RegisterRequest(null, "password123", "John", "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emailAlreadyTaken_returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class), anyString(), any()))
                .thenThrow(AuthException.emailTaken());

        RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "John", "Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =====================================================================
    // POST /api/v1/auth/login
    // =====================================================================

    @Test
    void login_validRequest_returns200WithTokens() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(SAMPLE_RESPONSE);

        LoginRequest request = new LoginRequest("user@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-uuid"));
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        // password is null — violates @NotBlank
        LoginRequest request = new LoginRequest("user@example.com", null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_tooManyAttempts_returns429() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(AuthException.tooManyAttempts());

        LoginRequest request = new LoginRequest("user@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    // =====================================================================
    // POST /api/v1/auth/refresh
    // =====================================================================

    @Test
    void refresh_validToken_returns200WithTokens() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
                .thenReturn(SAMPLE_RESPONSE);

        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-uuid"));
    }

    @Test
    void refresh_blankRefreshToken_returns400() throws Exception {
        // refreshToken is blank — violates @NotBlank
        RefreshRequest request = new RefreshRequest("   ");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
