package com.tsu.todolist.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Teste unitário puro (sem Spring): rápido e isolado. */
class JwtServiceTest {

    // Segredo de 32 bytes codificado em Base64
    private static final String SECRET =
            Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());

    private final UserPrincipal user = new UserPrincipal(1L, "Ana", "ana@email.com", "hash");

    @Test
    void generatedToken_containsEmailAndIsValid() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.generateToken("ana@email.com");

        assertEquals("ana@email.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void token_ofAnotherUser_isNotValid() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken("outro@email.com");

        assertFalse(jwtService.isTokenValid(token, user));
    }

    @Test
    void expiredToken_isRejected() {
        JwtService jwtService = new JwtService(SECRET, -1_000); // já nasce expirado
        String token = jwtService.generateToken("ana@email.com");

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
    }

    @Test
    void tamperedToken_isRejected() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken("ana@email.com");
        String tampered = token.substring(0, token.length() - 2) + "xx"; // altera a assinatura

        assertThrows(JwtException.class, () -> jwtService.extractUsername(tampered));
    }
}
