package com.tuling.tim.server.message;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowflakeIdGeneratorTest {
    @Test
    void generatedIdsAreUnique() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) ids.add(generator.nextId());
        assertEquals(10_000, ids.size());
    }
}
