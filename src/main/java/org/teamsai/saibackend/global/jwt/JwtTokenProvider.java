package org.teamsai.saibackend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.global.util.LinkIdentityHasher;

import javax.crypto.SecretKey;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_IDENTITY_HASH = "identity-hash";
    private static final String PURPOSE_ACCESS = "access";
    private static final String PURPOSE_BANK_LINK = "bank-link";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long linkStateExpirationMs;
    private final String linkIdentityHashSecret;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.link-state-expiration-ms}") long linkStateExpirationMs,
            @Value("${link-identity.hash-secret}") String linkIdentityHashSecret
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.linkStateExpirationMs = linkStateExpirationMs;
        this.linkIdentityHashSecret = linkIdentityHashSecret;
    }

    public String createAccessToken(Long userId) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public String createLinkStateToken(Long userId, String name, LocalDate birthDate) {
        if (name == null || name.isBlank() || birthDate == null) {
            throw UserErrorCode.INCOMPLETE_PROFILE_FOR_LINK.toException();
        }

        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + linkStateExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_PURPOSE, PURPOSE_BANK_LINK)
                .claim(CLAIM_IDENTITY_HASH, LinkIdentityHasher.hash(name, birthDate, linkIdentityHashSecret))
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public Optional<Long> getUserIdFromLinkState(String token) {
        return getUserIdIfPurposeMatches(token, PURPOSE_BANK_LINK);
    }

    public Optional<Long> getUserIdIfValid(String token) {
        return getUserIdIfPurposeMatches(token, PURPOSE_ACCESS);
    }

    private Optional<Long> getUserIdIfPurposeMatches(String token, String expectedPurpose) {
        try {
            Claims claims = parseClaims(token);

            if (!expectedPurpose.equals(claims.get(CLAIM_PURPOSE, String.class))) {
                return Optional.empty();
            }

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}