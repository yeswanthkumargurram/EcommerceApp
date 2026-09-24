package com.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

public class JwtProvider {
    private final Key key;
    private final long expirationMs = 1000L * 60 * 60 * 24; // 24h

    public JwtProvider(String secret) {
        if (secret == null || secret.length() < 32) {
            secret = "0123456789abcdef0123456789abcdef"; // default 32-bytes
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String subject) {
        return generateToken(subject, Map.of());
    }

    /** Embeds extra claims (e.g. userId, role) so downstream services can authorize
     * without an extra lookup call back to user-service. */
    public String generateToken(String subject, Map<String, Object> extraClaims) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateAndGetClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}
