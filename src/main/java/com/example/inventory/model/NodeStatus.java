package com.example.inventory.model;

import java.util.Locale;
import java.util.Optional;

/** Operational status of a network node. */
public enum NodeStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE,
    FAULTY;

    /** Case-insensitive, whitespace-tolerant lookup. Returns empty for null or unknown values. */
    public static Optional<NodeStatus> fromString(String value) {
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
