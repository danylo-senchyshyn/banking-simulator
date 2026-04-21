package com.banking.auth.service;

import com.banking.common.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;

    public boolean isBlocked(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(AppConstants.Redis.LOGIN_BLOCKED_PREFIX + ip));
    }

    public void recordFailedAttempt(String ip) {
        String attemptsKey = AppConstants.Redis.LOGIN_ATTEMPTS_PREFIX + ip;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, AppConstants.Redis.LOGIN_BLOCK_DURATION);

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(
                    AppConstants.Redis.LOGIN_BLOCKED_PREFIX + ip, "1", AppConstants.Redis.LOGIN_BLOCK_DURATION);
            redisTemplate.delete(attemptsKey);
        }
    }

    public void clearAttempts(String ip) {
        redisTemplate.delete(AppConstants.Redis.LOGIN_ATTEMPTS_PREFIX + ip);
        redisTemplate.delete(AppConstants.Redis.LOGIN_BLOCKED_PREFIX + ip);
    }
}
