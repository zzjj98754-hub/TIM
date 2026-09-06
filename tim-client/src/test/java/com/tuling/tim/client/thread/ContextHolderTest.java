package com.tuling.tim.client.thread;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextHolderTest {

    @AfterEach
    void clearThreadLocal() {
        ContextHolder.clear();
    }

    @Test
    void defaultsToInitialConnectionInsteadOfNull() {
        assertFalse(ContextHolder.getReconnect());
        ContextHolder.setReconnect(true);
        assertTrue(ContextHolder.getReconnect());
    }
}
