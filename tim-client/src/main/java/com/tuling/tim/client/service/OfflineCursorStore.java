package com.tuling.tim.client.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Keeps the largest continuous offline cursor processed by this client session. */
@Component
public class OfflineCursorStore {
    private final AtomicLong cursor = new AtomicLong();
    public long current() { return cursor.get(); }
    public void advance(long next) { cursor.accumulateAndGet(next, Math::max); }
}
