package com.example.robotpathplanning.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** JWT access tokenini yaradır və yoxlayır (HS512). */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration ttl;

    public JwtProvider(AuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.tokenTtl();
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /** Token düzdürsə (imza doğru, vaxtı keçməyib) istifadəçi adını qaytarır, əks halda boş. */
    public Optional<String> getUsername(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
