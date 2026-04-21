package com.banking.auth.service;

import com.banking.common.AppConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RateLimitService rateLimitService;

    private static final String IP = "192.168.1.1";
    private static final String BLOCKED_KEY  = AppConstants.Redis.LOGIN_BLOCKED_PREFIX + IP;
    private static final String ATTEMPTS_KEY = AppConstants.Redis.LOGIN_ATTEMPTS_PREFIX + IP;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // --- isBlocked ---

    @Test
    void isBlocked_keyExists_returnsTrue() {
        when(redisTemplate.hasKey(BLOCKED_KEY)).thenReturn(true);
        assertThat(rateLimitService.isBlocked(IP)).isTrue();
    }

    @Test
    void isBlocked_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey(BLOCKED_KEY)).thenReturn(false);
        assertThat(rateLimitService.isBlocked(IP)).isFalse();
    }

    @Test
    void isBlocked_redisReturnsNull_returnsFalse() {
        when(redisTemplate.hasKey(BLOCKED_KEY)).thenReturn(null);
        assertThat(rateLimitService.isBlocked(IP)).isFalse();
    }

    // --- recordFailedAttempt: below threshold ---

    @Test
    void recordFailedAttempt_belowThreshold_doesNotBlock() {
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(3L);

        rateLimitService.recordFailedAttempt(IP);

        verify(valueOps, never()).set(eq(BLOCKED_KEY), any(), any());
    }

    @Test
    void recordFailedAttempt_belowThreshold_setsAttemptsTtl() {
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(3L);

        rateLimitService.recordFailedAttempt(IP);

        verify(redisTemplate).expire(eq(ATTEMPTS_KEY), any(Duration.class));
    }

    // --- recordFailedAttempt: at threshold ---

    @Test
    void recordFailedAttempt_atThreshold_blocksIp() {
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(5L);

        rateLimitService.recordFailedAttempt(IP);

        verify(valueOps).set(eq(BLOCKED_KEY), eq("1"), any(Duration.class));
    }

    @Test
    void recordFailedAttempt_atThreshold_deletesAttemptsKey() {
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(5L);

        rateLimitService.recordFailedAttempt(IP);

        verify(redisTemplate).delete(ATTEMPTS_KEY);
    }

    @Test
    void recordFailedAttempt_aboveThreshold_alsoBlocks() {
        when(valueOps.increment(ATTEMPTS_KEY)).thenReturn(10L);

        rateLimitService.recordFailedAttempt(IP);

        verify(valueOps).set(eq(BLOCKED_KEY), eq("1"), any(Duration.class));
    }

    // --- clearAttempts ---

    @Test
    void clearAttempts_deletesAttemptsAndBlockedKeys() {
        rateLimitService.clearAttempts(IP);

        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(redisTemplate).delete(BLOCKED_KEY);
    }
}
