package com.banking.auth.security;

import com.banking.auth.domain.Role;
import com.banking.auth.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-at-least-32-characters-long");
        jwtProperties.setAccessTokenExpiration(900);
        jwtProperties.setRefreshTokenExpiration(604800);
        jwtService = new JwtService(jwtProperties);
    }

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPassword("hashed");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void generateAccessToken_returnsNonBlankToken() {
        String token = jwtService.generateAccessToken(buildUser());
        assertThat(token).isNotBlank();
    }

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(buildUser());
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(buildUser()) + "tampered";
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_randomString_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void extractUserId_returnsCorrectId() {
        User user = buildUser();
        String token = jwtService.generateAccessToken(user);
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId().toString());
    }

    @Test
    void parseToken_containsEmailAndRoleClaims() {
        User user = buildUser();
        String token = jwtService.generateAccessToken(user);
        var claims = jwtService.parseToken(token);

        assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
        assertThat(claims.get("role", String.class)).isEqualTo(Role.USER.name());
    }

    @Test
    void generateAccessToken_expiredToken_isInvalid() {
        jwtProperties.setAccessTokenExpiration(-1);
        String token = jwtService.generateAccessToken(buildUser());
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void generateAccessToken_differentUsersProduceDifferentTokens() {
        User user1 = buildUser();
        User user2 = buildUser();
        user2.setId(UUID.randomUUID());

        assertThat(jwtService.generateAccessToken(user1))
                .isNotEqualTo(jwtService.generateAccessToken(user2));
    }
}
