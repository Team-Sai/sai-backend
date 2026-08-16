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

    private static final long REFRESH_TOKEN_EXPIRATION_MS =
            14 * 24 * 60 * 60 * 1000L;

    private static final long LINK_STATE_EXPIRATION_MS =
            10 * 60 * 1000L;

    private static final String LINK_IDENTITY_HASH_SECRET =
            "test-link-identity-secret";

    private static final Long USER_ID = 1L;

    private String accessSecret;
    private String linkStateSecret;

    private SecretKey accessSigningKey;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        byte[] accessKeyBytes =
                "01234567890123456789012345678901"
                        .getBytes(StandardCharsets.UTF_8);

        byte[] linkStateKeyBytes =
                "98765432109876543210987654321098"
                        .getBytes(StandardCharsets.UTF_8);

        accessSecret =
                Encoders.BASE64.encode(accessKeyBytes);

        linkStateSecret =
                Encoders.BASE64.encode(linkStateKeyBytes);

        accessSigningKey =
                Keys.hmacShaKeyFor(accessKeyBytes);

        jwtTokenProvider =
                new JwtTokenProvider(
                        accessSecret,
                        linkStateSecret,
                        ACCESS_TOKEN_EXPIRATION_MS,
                        REFRESH_TOKEN_EXPIRATION_MS,
                        LINK_STATE_EXPIRATION_MS,
                        LINK_IDENTITY_HASH_SECRET
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
        String token =
                createAccessToken(
                        "SAI-ABCDEFGH",
                        new Date(
                                System.currentTimeMillis()
                                        + ACCESS_TOKEN_EXPIRATION_MS
                        ),
                        accessSigningKey
                );

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId)
                .isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰은 유효하지 않은 토큰으로 처리한다")
    void expiredTokenIsInvalid() {
        String token =
                createAccessToken(
                        String.valueOf(USER_ID),
                        new Date(
                                System.currentTimeMillis()
                                        - 1000L
                        ),
                        accessSigningKey
                );

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId)
                .isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 유효하지 않은 토큰으로 처리한다")
    void tokenSignedWithDifferentKeyIsInvalid() {
        byte[] otherKeyBytes =
                "abcdefghijklmnopqrstuvwxyz123456"
                        .getBytes(StandardCharsets.UTF_8);

        SecretKey otherSigningKey =
                Keys.hmacShaKeyFor(otherKeyBytes);

        String token =
                createAccessToken(
                        String.valueOf(USER_ID),
                        new Date(
                                System.currentTimeMillis()
                                        + ACCESS_TOKEN_EXPIRATION_MS
                        ),
                        otherSigningKey
                );

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(token);

        assertThat(parsedUserId)
                .isEmpty();
    }

    @Test
    @DisplayName("RefreshToken은 AccessToken으로 사용할 수 없다")
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken =
                jwtTokenProvider.createRefreshToken(USER_ID);

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdIfValid(refreshToken);

        assertThat(parsedUserId)
                .isEmpty();
    }

    @Test
    @DisplayName("RefreshToken에서 userId를 정상적으로 추출한다")
    void getUserIdFromRefreshToken() {
        String refreshToken =
                jwtTokenProvider.createRefreshToken(USER_ID);

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdFromRefreshToken(
                        refreshToken
                );

        assertThat(parsedUserId)
                .contains(USER_ID);
    }

    @Test
    @DisplayName("createAccessToken으로 만든 토큰은 getUserIdFromLinkState로 검증되지 않는다")
    void accessTokenIsNotValidAsLinkState() {
        String accessToken =
                jwtTokenProvider.createAccessToken(USER_ID);

        Optional<Long> parsedUserId =
                jwtTokenProvider.getUserIdFromLinkState(
                        accessToken
                );

        assertThat(parsedUserId)
                .isEmpty();
    }

    private String createAccessToken(
            String subject,
            Date expiration,
            SecretKey key
    ) {
        Date issuedAt = new Date();

        return Jwts.builder()
                .subject(subject)
                .claim(
                        "purpose",
                        "access"
                )
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}