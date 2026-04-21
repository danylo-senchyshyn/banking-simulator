package com.banking.auth.service;

import com.banking.auth.domain.RefreshToken;
import com.banking.auth.domain.Role;
import com.banking.auth.domain.User;
import com.banking.auth.exception.AuthException;
import com.banking.auth.repository.RefreshTokenRepository;
import com.banking.auth.repository.UserRepository;
import com.banking.auth.security.JwtProperties;
import com.banking.auth.security.JwtService;
import com.banking.auth.security.TokenHasher;
import com.banking.auth.web.dto.*;
import com.banking.common.AppConstants;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {


    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimitService;
    private final AuditService auditService;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ip, String idempotencyKey) {
        if (idempotencyKey != null) {
            String redisKey = AppConstants.Redis.IDEMPOTENCY_REGISTER_PREFIX + idempotencyKey;
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
                log.warn("Duplicate registration request detected: idempotencyKey={}", idempotencyKey);
                throw AuthException.duplicateRequest("Duplicate registration request");
            }
        }

        String email = request.email().trim();
        log.info("Register attempt: email={}, ip={}", email, ip);

        if (userRepository.existsByEmail(email)) {
            log.warn("Register failed — email taken: {}", email);
            throw AuthException.emailTaken();
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.USER)
                .blocked(false)
                .build());

        if (idempotencyKey != null) {
            stringRedisTemplate.opsForValue().set(AppConstants.Redis.IDEMPOTENCY_REGISTER_PREFIX + idempotencyKey, "processed", AppConstants.Redis.IDEMPOTENCY_TTL);
        }

        log.info("User registered: id={}, email={}", user.getId(), email);
        auditService.log(user, "REGISTER", ip, null);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        log.info("Login attempt: email={}, ip={}", request.email(), ip);
        if (rateLimitService.isBlocked(ip)) {
            log.warn("Login blocked by rate limit: ip={}", ip);
            throw AuthException.tooManyAttempts();
        }

        User user = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> {
                    rateLimitService.recordFailedAttempt(ip);
                    return AuthException.invalidCredentials();
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            rateLimitService.recordFailedAttempt(ip);
            auditService.log(user, "LOGIN_FAILED", ip, null);
            throw AuthException.invalidCredentials();
        }

        if (user.isBlocked()) {
            auditService.log(user, "LOGIN_BLOCKED", ip, null);
            throw AuthException.accountBlocked();
        }

        rateLimitService.clearAttempts(ip);
        log.info("Login success: userId={}, email={}", user.getId(), user.getEmail());
        auditService.log(user, "LOGIN", ip, null);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = TokenHasher.hash(request.refreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(AuthException::invalidToken);

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw AuthException.invalidToken();
        }

        User user = refreshToken.getUser();
        if (user.isBlocked()) throw AuthException.accountBlocked();

        refreshTokenRepository.delete(refreshToken);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        String plainToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHasher.hash(plainToken))
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpiration()))
                .build());

        return new AuthResponse(accessToken, plainToken);
    }
}
