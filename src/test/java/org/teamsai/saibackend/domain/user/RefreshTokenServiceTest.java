package org.teamsai.saibackend.domain.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.user.service.RefreshTokenService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService 단위 테스트")
class RefreshTokenServiceTest {

    private static final Long USER_ID = 1L;

    private static final String REFRESH_TOKEN =
            "refresh-token";

    private static final long REFRESH_TOKEN_EXPIRATION_MS =
            14 * 24 * 60 * 60 * 1000L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                refreshTokenService,
                "refreshTokenExpirationMs",
                REFRESH_TOKEN_EXPIRATION_MS
        );
    }

    @Test
    @DisplayName("RefreshToken을 만료시간과 함께 Redis에 저장한다")
    void saveRefreshToken() {
        given(
                redisTemplate.opsForValue()
        ).willReturn(
                valueOperations
        );

        refreshTokenService.save(
                USER_ID,
                REFRESH_TOKEN
        );

        verify(valueOperations)
                .set(
                        "auth:refresh:" + USER_ID,
                        REFRESH_TOKEN,
                        Duration.ofMillis(
                                REFRESH_TOKEN_EXPIRATION_MS
                        )
                );
    }

    @Test
    @DisplayName("Redis에 저장된 RefreshToken과 같으면 true를 반환한다")
    void matchesRefreshToken() {
        given(
                redisTemplate.opsForValue()
        ).willReturn(
                valueOperations
        );

        given(
                valueOperations.get(
                        "auth:refresh:" + USER_ID
                )
        ).willReturn(
                REFRESH_TOKEN
        );

        boolean result =
                refreshTokenService.matches(
                        USER_ID,
                        REFRESH_TOKEN
                );

        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("로그아웃 시 Redis의 RefreshToken을 삭제한다")
    void deleteRefreshToken() {
        refreshTokenService.delete(
                USER_ID
        );

        verify(redisTemplate)
                .delete(
                        "auth:refresh:" + USER_ID
                );
    }
}