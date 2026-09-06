package com.tuling.tim.client.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDeduplicatorTest {
    @Test
    void onlyFirstDeliveryIsNew() {
        MessageDeduplicator deduplicator = new MessageDeduplicator();
        assertTrue(deduplicator.firstDelivery("m-1"));
        assertFalse(deduplicator.firstDelivery("m-1"));
        assertTrue(deduplicator.firstDelivery("m-2"));
    }
}
