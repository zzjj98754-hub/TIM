package com.tuling.tim.server.group;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Centralizes the configurable boundary between member inbox and group cursor fanout. */
@Component
public class GroupFanoutStrategySelector {
    private final int writeFanoutLimit;

    public GroupFanoutStrategySelector(@Value("${tim.group.write-fanout-limit:500}") int writeFanoutLimit) {
        if (writeFanoutLimit <= 0) throw new IllegalArgumentException("write fanout limit must be positive");
        this.writeFanoutLimit = writeFanoutLimit;
    }

    public GroupFanoutStrategy select(int memberCount) {
        return memberCount < writeFanoutLimit ? GroupFanoutStrategy.WRITE : GroupFanoutStrategy.READ;
    }
}
