package com.tuling.tim.client.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OfflineCursorStoreTest {
    @Test void advancesOnlyAcrossAContinuousPrefix() {
        OfflineCursorStore store = new OfflineCursorStore(20L);
        store.advance(22); store.advance(23);
        assertEquals(0L, store.current());
        store.advance(21);
        assertEquals(23L, store.current());
    }
}
