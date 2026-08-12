package org.teamsai.saibackend.domain.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.global.jwt.JwtTokenProvider;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    private static final long ACCESS_TOKEN_EXPIRATION_MS =
            60 * 60 * 1000L;
    private static final long LINK_STATE_EXPIRATION_MS =
            10 * 60 * 1000L;
    private static final Long USER_ID = 1L;
    private static final String TEST_LINK_IDENTITY_HASH_SECRET = "test-link-identity-secret";

    private String secret;
    private SecretKey signingKey;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        byte[] keyBytes =
                "01234567890123456789012345678901"
                        .getBytes(StandardCharsets.UTF_8);
        secret = Encoders.BASE64.encode(keyBytes);
        signingKey = Keys.hmacShaKeyFor(keyBytes);

        jwtTokenProvider = new JwtTokenProvider(
                secret,
                ACCESS_TOKEN_EXPIRATION_MS,
                LINK_STATE_EXPIRATION_MS,
                TEST_LINK_IDENTITY_HASH_SECRET
        );
    }

    @Test
    @DisplayName("userId를 subject로 저장하고 다시 Long으로 반환한다")
    void createAndParseUserIdToken() {
        String token =
                jwtTokenProvider.createAccessToken(USER_ID);

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId)
                .contains(USER_ID);
    }

    @Test
    @DisplayName("subject가 숫자가 아니면 유효하지 않은 토큰으로 처리한다")
    void nonNumericSubjectIsInvalid() {
        String token = createToken(
                "SAI-ABCDEFGH",
                new Date(
                        System.currentTimeMillis()
                                + ACCESS_TOKEN_EXPIRATION_MS
                ),
                signingKey
        );

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 유효하지 않은 토큰으로 처리한다")
    void expiredTokenIsInvalid() {
        String token = createToken(
                String.valueOf(USER_ID),
                new Date(
                        System.currentTimeMillis()
                                - 1000L
                ),
                signingKey
        );

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 유효하지 않은 토큰으로 처리한다")
    void tokenSignedWithDifferentKeyIsInvalid() {
        byte[] otherKeyBytes =
                "abcdefghijklmnopqrstuvwxyz123456"
                        .getBytes(StandardCharsets.UTF_8);
        SecretKey otherSigningKey =
                Keys.hmacShaKeyFor(otherKeyBytes);

        String token = createToken(
                String.valueOf(USER_ID),
                new Date(
                        System.currentTimeMillis()
                                + ACCESS_TOKEN_EXPIRATION_MS
                ),
                otherSigningKey
        );

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId).isEmpty();
    }

    private String createToken(
            String subject,
            Date expiration,
            SecretKey key
    ) {
        Date issuedAt = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}