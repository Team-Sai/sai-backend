package org.teamsai.saibackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    public void save(Long userId, String refreshToken){
        redisTemplate.opsForValue().set(
                createKey(userId),
                refreshToken,
                Duration.ofMillis(refreshTokenExpirationMs));
    }

    public boolean matches(Long userId, String refreshToken
    ) {
        String storedRefreshToken = redisTemplate.opsForValue()
                        .get(createKey(userId));

        if (refreshToken == null || storedRefreshToken == null
        ) {
            return false;
        }

        return MessageDigest.isEqual(
                refreshToken.getBytes(
                        StandardCharsets.UTF_8
                ),
                storedRefreshToken.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    public void delete(Long userId){
        redisTemplate.delete(createKey(userId));
    }

    private String createKey(Long userId){
        return KEY_PREFIX + userId;
    }
}
