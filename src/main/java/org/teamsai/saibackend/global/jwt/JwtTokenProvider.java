package org.teamsai.saibackend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}")
            long accessTokenExpirationMs
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String createAccessToken(Long userId) {
        Date issuedAt = new Date();
        Date expiration = new Date(
                issuedAt.getTime() + accessTokenExpirationMs
        );

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public Optional<Long> getUserIdIfValid(String token) {
        try {
            Claims claims = parseClaims(token);
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