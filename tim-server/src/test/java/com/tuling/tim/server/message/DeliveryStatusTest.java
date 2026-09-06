package com.tuling.tim.server.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryStatusTest {
    @Test
    void terminalStatesCannotRegress() {
        assertTrue(DeliveryStatus.DELIVERING.canTransitionTo(DeliveryStatus.ACKED));
        assertTrue(DeliveryStatus.OFFLINE.canTransitionTo(DeliveryStatus.DELIVERING));
        assertFalse(DeliveryStatus.ACKED.canTransitionTo(DeliveryStatus.OFFLINE));
        assertFalse(DeliveryStatus.DEAD.canTransitionTo(DeliveryStatus.DELIVERING));
    }
}
