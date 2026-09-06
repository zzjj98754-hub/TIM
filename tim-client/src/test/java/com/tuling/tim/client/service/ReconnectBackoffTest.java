package com.tuling.tim.client.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconnectBackoffTest {
    @Test
    void usesExponentialDelaysAndCapsAtThirtySeconds() {
        assertEquals(1, ReconnectBackoff.baseDelaySeconds(0));
        assertEquals(2, ReconnectBackoff.baseDelaySeconds(1));
        assertEquals(4, ReconnectBackoff.baseDelaySeconds(2));
        assertEquals(8, ReconnectBackoff.baseDelaySeconds(3));
        assertEquals(16, ReconnectBackoff.baseDelaySeconds(4));
        assertEquals(30, ReconnectBackoff.baseDelaySeconds(5));
        assertEquals(30, ReconnectBackoff.baseDelaySeconds(20));
    }
}
