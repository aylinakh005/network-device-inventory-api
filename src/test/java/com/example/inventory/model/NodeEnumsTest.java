package com.example.inventory.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NodeEnumsTest {

    @Test
    void typeParsingIsCaseInsensitiveAndTrimmed() {
        assertEquals(Optional.of(NodeType.BASE_STATION), NodeType.fromString(" base_station "));
        assertEquals(Optional.of(NodeType.ROUTER), NodeType.fromString("Router"));
    }

    @Test
    void typeParsingReturnsEmptyForNullOrUnknown() {
        assertTrue(NodeType.fromString(null).isEmpty());
        assertTrue(NodeType.fromString("").isEmpty());
        assertTrue(NodeType.fromString("modem").isEmpty());
    }

    @Test
    void statusParsingIsCaseInsensitiveAndTrimmed() {
        assertEquals(Optional.of(NodeStatus.MAINTENANCE), NodeStatus.fromString("  Maintenance"));
    }

    @Test
    void statusParsingReturnsEmptyForNullOrUnknown() {
        assertTrue(NodeStatus.fromString(null).isEmpty());
        assertTrue(NodeStatus.fromString("sleeping").isEmpty());
    }
}
