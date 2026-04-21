package com.banking.auth.integration;

import com.banking.auth.repository.RefreshTokenRepository;
import com.banking.auth.repository.UserRepository;
import com.banking.auth.service.AuditService;
import com.banking.auth.service.AuthService;
import com.banking.auth.web.dto.AuthResponse;
import com.banking.auth.web.dto.LoginRequest;
import com.banking.auth.web.dto.RefreshRequest;
import com.banking.auth.web.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockBean
    AuditService auditService;

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_persistsUserToDatabase() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123", "Alice", "Smith");

        AuthResponse response = authService.register(request, "127.0.0.1", null);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(userRepository.findByEmail("alice@example.com")).isPresent();
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        RegisterRequest request = new RegisterRequest("bob@example.com", "password123", "Bob", "Jones");
        authService.register(request, "127.0.0.1", null);

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void login_validCredentials_returnsTokens() {
        RegisterRequest reg = new RegisterRequest("carol@example.com", "securePass1", "Carol", "White");
        authService.register(reg, "127.0.0.1", null);

        AuthResponse response = authService.login(new LoginRequest("carol@example.com", "securePass1"), "127.0.0.1");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        RegisterRequest reg = new RegisterRequest("dave@example.com", "correctPass1", "Dave", "Brown");
        authService.register(reg, "127.0.0.1", null);

        assertThatThrownBy(() -> authService.login(new LoginRequest("dave@example.com", "wrongPass"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void refresh_validToken_returnsNewTokens() {
        RegisterRequest reg = new RegisterRequest("eve@example.com", "password123", "Eve", "Green");
        AuthResponse registered = authService.register(reg, "127.0.0.1", null);

        AuthResponse refreshed = authService.refresh(new RefreshRequest(registered.refreshToken()));

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(registered.refreshToken());
    }

    @Test
    void refresh_usedToken_throwsUnauthorized() {
        RegisterRequest reg = new RegisterRequest("frank@example.com", "password123", "Frank", "Black");
        AuthResponse registered = authService.register(reg, "127.0.0.1", null);

        authService.refresh(new RefreshRequest(registered.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(registered.refreshToken())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired token");
    }

    @Test
    void logout_deletesAllRefreshTokens() {
        RegisterRequest reg = new RegisterRequest("grace@example.com", "password123", "Grace", "Hall");
        AuthResponse registered = authService.register(reg, "127.0.0.1", null);

        var user = userRepository.findByEmail("grace@example.com").orElseThrow();
        authService.logout(user.getId());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(registered.refreshToken())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired token");
    }

    @Test
    void register_withIdempotencyKey_duplicateThrowsConflict() {
        RegisterRequest request = new RegisterRequest("henry@example.com", "password123", "Henry", "Ford");
        String idempotencyKey = "unique-key-001";

        authService.register(request, "127.0.0.1", idempotencyKey);

        RegisterRequest second = new RegisterRequest("henry2@example.com", "password123", "Henry", "Ford");
        assertThatThrownBy(() -> authService.register(second, "127.0.0.1", idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Duplicate registration request");
    }
}
