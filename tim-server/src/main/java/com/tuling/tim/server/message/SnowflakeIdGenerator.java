package com.tuling.tim.server.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Small synchronized Snowflake variant: timestamp + node id + per-millisecond sequence. */
@Component
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1704067200000L;
    private long lastTimestamp = -1L;
    private long sequence;

    @Value("${tim.node.id:1}")
    private long nodeId;

    public synchronized String nextId() {
        long now = System.currentTimeMillis();
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & 0xFFF;
            if (sequence == 0) {
                while ((now = System.currentTimeMillis()) == lastTimestamp) { Thread.onSpinWait(); }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return Long.toUnsignedString(((now - EPOCH) << 22) | ((nodeId & 0x3FF) << 12) | sequence);
    }
}
