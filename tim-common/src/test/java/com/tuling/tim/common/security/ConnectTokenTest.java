package com.tuling.tim.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectTokenTest {
    @Test
    void verifiesValidTokenAndBindsAudience() {
        String token = ConnectToken.issue(42L, "node-1", 60_000, "test-secret");
        ConnectToken.Claims claims = ConnectToken.verify(token, "node-1", "test-secret", System.currentTimeMillis());
        assertEquals(42L, claims.userId());
        assertEquals("node-1", claims.serverId());
    }

    @Test
    void rejectsTamperingExpiryAndWrongServer() {
        String token = ConnectToken.issue(42L, "node-1", 1, "test-secret");
        assertThrows(IllegalArgumentException.class,
                () -> ConnectToken.verify(token + "x", "node-1", "test-secret", System.currentTimeMillis()));
        assertThrows(IllegalArgumentException.class,
                () -> ConnectToken.verify(token, "node-2", "test-secret", System.currentTimeMillis()));
        assertThrows(IllegalArgumentException.class,
                () -> ConnectToken.verify(token, "node-1", "test-secret", System.currentTimeMillis() + 10_000));
    }
}
