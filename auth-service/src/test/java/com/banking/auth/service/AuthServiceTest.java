package com.banking.auth.service;

import com.banking.auth.domain.RefreshToken;
import com.banking.auth.domain.Role;
import com.banking.auth.domain.User;
import com.banking.auth.exception.AuthException;
import com.banking.auth.repository.RefreshTokenRepository;
import com.banking.auth.repository.UserRepository;
import com.banking.auth.security.JwtProperties;
import com.banking.auth.security.JwtService;
import com.banking.auth.web.dto.AuthResponse;
import com.banking.auth.web.dto.LoginRequest;
import com.banking.auth.web.dto.RefreshRequest;
import com.banking.auth.security.TokenHasher;
import com.banking.auth.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProperties;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RateLimitService rateLimitService;
    @Mock private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private static final String IP = "127.0.0.1";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123";
    private static final String HASHED = "$2a$hashed";
    private static final String ACCESS_TOKEN = "access.token.jwt";

    private User buildUser() {
        User user = User.builder()
                .email(EMAIL)
                .password(HASHED)
                .firstName("John")
                .lastName("Doe")
                .role(Role.USER)
                .blocked(false)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private String plainToken;

    private RefreshToken buildRefreshToken(User user) {
        plainToken = UUID.randomUUID().toString();
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(plainToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        rt.setId(UUID.randomUUID());
        return rt;
    }

    @BeforeEach
    void setUp() {
        lenient().when(jwtService.generateAccessToken(any())).thenReturn(ACCESS_TOKEN);
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800L);
        lenient().when(refreshTokenRepository.save(any())).thenAnswer(i -> {
            RefreshToken rt = i.getArgument(0);
            if (rt.getId() == null) rt.setId(UUID.randomUUID());
            return rt;
        });
    }

    // ===== register =====

    @Test
    void register_newEmail_returnsTokens() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);
        User saved = buildUser();
        when(userRepository.save(any())).thenReturn(saved);

        RegisterRequest req = new RegisterRequest(EMAIL, PASSWORD, "John", "Doe");
        AuthResponse response = authService.register(req, IP, null);

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "J", "D"), IP, null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void register_savesUserWithEncodedPassword() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);
        when(userRepository.save(any())).thenReturn(buildUser());

        authService.register(new RegisterRequest(EMAIL, PASSWORD, "J", "D"), IP, null);

        verify(passwordEncoder).encode(PASSWORD);
        verify(userRepository).save(argThat(u -> HASHED.equals(u.getPassword())));
    }

    @Test
    void register_logsAuditEvent() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn(HASHED);
        when(userRepository.save(any())).thenReturn(buildUser());

        authService.register(new RegisterRequest(EMAIL, PASSWORD, "J", "D"), IP, null);

        verify(auditService).log(any(), eq("REGISTER"), eq(IP), any());
    }

    // ===== login =====

    @Test
    void login_validCredentials_returnsTokens() {
        User user = buildUser();
        when(rateLimitService.isBlocked(IP)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD), IP);

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void login_blockedIp_throwsTooManyAttempts() {
        when(rateLimitService.isBlocked(IP)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void login_userNotFound_throwsUnauthorizedAndRecordsAttempt() {
        when(rateLimitService.isBlocked(IP)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(rateLimitService).recordFailedAttempt(IP);
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedAndRecordsAttempt() {
        User user = buildUser();
        when(rateLimitService.isBlocked(IP)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(rateLimitService).recordFailedAttempt(IP);
    }

    @Test
    void login_blockedUser_throwsForbidden() {
        User user = buildUser();
        user.setBlocked(true);
        when(rateLimitService.isBlocked(IP)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void login_successfulLogin_clearsRateLimit() {
        User user = buildUser();
        when(rateLimitService.isBlocked(IP)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);

        authService.login(new LoginRequest(EMAIL, PASSWORD), IP);

        verify(rateLimitService).clearAttempts(IP);
    }

    // ===== refresh =====

    @Test
    void refresh_validToken_returnsNewTokens() {
        User user = buildUser();
        RefreshToken rt = buildRefreshToken(user);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(plainToken))).thenReturn(Optional.of(rt));

        AuthResponse response = authService.refresh(new RefreshRequest(plainToken));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(refreshTokenRepository).delete(rt);
    }

    @Test
    void refresh_tokenNotFound_throwsUnauthorized() {
        String badToken = "bad-token";
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(badToken))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(badToken)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void refresh_expiredToken_deletesAndThrows() {
        User user = buildUser();
        String expiredPlain = "expired-token";
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(expiredPlain))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        rt.setId(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(expiredPlain))).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(expiredPlain)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(refreshTokenRepository).delete(rt);
    }

    @Test
    void refresh_blockedUser_throwsForbidden() {
        User user = buildUser();
        user.setBlocked(true);
        RefreshToken rt = buildRefreshToken(user);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash(plainToken))).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(plainToken)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ===== logout =====

    @Test
    void logout_deletesAllRefreshTokensForUser() {
        UUID userId = UUID.randomUUID();
        authService.logout(userId);
        verify(refreshTokenRepository).deleteAllByUserId(userId);
    }
}
