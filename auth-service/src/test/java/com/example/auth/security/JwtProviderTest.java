package com.example.auth.security;

import com.example.common.security.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void validatesGeneratedTokenAndRejectsExpiredToken() {
        JwtProvider provider = new JwtProvider(SECRET);
        assertEquals("ada@example.com", provider.validateAndGetClaims(provider.generateToken("ada@example.com")).getSubject());

        String expiredToken = Jwts.builder()
                .setSubject("ada@example.com")
                .setExpiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        assertThrows(Exception.class, () -> provider.validateAndGetClaims(expiredToken));
    }
}