package com.example.inventory.model;

import java.util.Locale;
import java.util.Optional;

/** Kinds of network nodes the inventory can track. */
public enum NodeType {
    ROUTER,
    SWITCH,
    FIREWALL,
    BASE_STATION,
    CORE_GATEWAY,
    ACCESS_POINT,
    LOAD_BALANCER;

    /** Case-insensitive, whitespace-tolerant lookup. Returns empty for null or unknown values. */
    public static Optional<NodeType> fromString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
