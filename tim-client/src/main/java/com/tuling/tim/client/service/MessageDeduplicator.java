package com.tuling.tim.client.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded client-side de-duplication for at-least-once server delivery. */
@Component
public class MessageDeduplicator {
    private static final int MAX_ENTRIES = 4096;
    private final Map<String, Boolean> seen = new LinkedHashMap<>(MAX_ENTRIES, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized boolean firstDelivery(String messageId) {
        if (messageId == null || messageId.isBlank()) return true;
        if (seen.containsKey(messageId)) return false;
        seen.put(messageId, Boolean.TRUE);
        return true;
    }
}
