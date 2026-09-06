package com.tuling.tim.server.message;

import java.util.EnumSet;
import java.util.Map;

/** Durable private-message delivery states and the only legal forward transitions. */
public enum DeliveryStatus {
    PENDING, ROUTING, DELIVERING, ACKED, OFFLINE, DEAD;

    private static final Map<DeliveryStatus, EnumSet<DeliveryStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(ROUTING, DELIVERING, OFFLINE),
            ROUTING, EnumSet.of(DELIVERING, OFFLINE, DEAD),
            DELIVERING, EnumSet.of(DELIVERING, ACKED, OFFLINE, DEAD),
            OFFLINE, EnumSet.of(DELIVERING, ACKED, DEAD),
            ACKED, EnumSet.noneOf(DeliveryStatus.class),
            DEAD, EnumSet.noneOf(DeliveryStatus.class));

    public boolean canTransitionTo(DeliveryStatus next) {
        return next != null && (this == next || TRANSITIONS.get(this).contains(next));
    }
}
