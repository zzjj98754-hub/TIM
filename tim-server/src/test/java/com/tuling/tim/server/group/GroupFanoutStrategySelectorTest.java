package com.tuling.tim.server.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupFanoutStrategySelectorTest {
    @Test
    void thresholdBelongsToReadFanout() {
        GroupFanoutStrategySelector selector = new GroupFanoutStrategySelector(500);
        assertEquals(GroupFanoutStrategy.WRITE, selector.select(499));
        assertEquals(GroupFanoutStrategy.READ, selector.select(500));
    }
}
